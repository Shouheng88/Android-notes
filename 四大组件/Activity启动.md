# Android 系统启动过程分析

系统启动的第一个进程是 init 进程，相关源码在 system/core/init 中

1. 加载引导程序 BootLoader 到 RAM
2. 执行引导程序 BootLoader 以把系统 OS 拉起来
3. 启动 Linux 内核，然后在系统文件中寻找 init.rc 文件并启动 init 进程
4. 启动 init 进程，初始化和启动属性服务，启动 Zygote 进程等

## 1 启动 init 进程

```C++
    // system/core/init/init.cpp
    int main(int argc, char** argv) {
    #if __has_feature(address_sanitizer)
        __asan_set_error_report_callback(AsanReportCallback);
    #endif

        if (!strcmp(basename(argv[0]), "ueventd")) {
            return ueventd_main(argc, argv);
        }

        if (argc > 1 && !strcmp(argv[1], "subcontext")) {
            android::base::InitLogging(argv, &android::base::KernelLogger);
            const BuiltinFunctionMap function_map;
            return SubcontextMain(argc, argv, &function_map);
        }

        if (REBOOT_BOOTLOADER_ON_PANIC) {
            InstallRebootSignalHandlers();
        }

        if (getenv("SELINUX_INITIALIZED") == nullptr) {
            SetupSelinux(argv);
        }

        // We need to set up stdin/stdout/stderr again now that we're running in init's context.
        InitKernelLogging(argv, InitAborter);
        LOG(INFO) << "init second stage started!";

        // Enable seccomp if global boot option was passed (otherwise it is enabled in zygote).
        GlobalSeccomp();

        // Set up a session keyring that all processes will have access to. It
        // will hold things like FBE encryption keys. No process should override
        // its session keyring.
        keyctl_get_keyring_ID(KEY_SPEC_SESSION_KEYRING, 1);

        // Indicate that booting is in progress to background fw loaders, etc.
        close(open("/dev/.booting", O_WRONLY | O_CREAT | O_CLOEXEC, 0000));

        property_init(); // 初始化属性服务

        // If arguments are passed both on the command line and in DT,
        // properties set in DT always have priority over the command-line ones.
        process_kernel_dt();
        process_kernel_cmdline();

        // Propagate the kernel variables to internal variables
        // used by init as well as the current required properties.
        export_kernel_boot_props();

        // Make the time that init started available for bootstat to log.
        property_set("ro.boottime.init", getenv("INIT_STARTED_AT"));
        property_set("ro.boottime.init.selinux", getenv("INIT_SELINUX_TOOK"));

        // Set libavb version for Framework-only OTA match in Treble build.
        const char* avb_version = getenv("INIT_AVB_VERSION");
        if (avb_version) property_set("ro.boot.avb_version", avb_version);

        // Clean up our environment.
        unsetenv("SELINUX_INITIALIZED");
        unsetenv("INIT_STARTED_AT");
        unsetenv("INIT_SELINUX_TOOK");
        unsetenv("INIT_AVB_VERSION");

        // Now set up SELinux for second stage.
        SelinuxSetupKernelLogging();
        SelabelInitialize();
        SelinuxRestoreContext();

        Epoll epoll;
        if (auto result = epoll.Open(); !result) {
            PLOG(FATAL) << result.error();
        }

        InstallSignalFdHandler(&epoll);

        property_load_boot_defaults();
        fs_mgr_vendor_overlay_mount_all();
        export_oem_lock_status();
        StartPropertyService(&epoll); // 启动属性服务
        set_usb_controller();

        const BuiltinFunctionMap function_map;
        Action::set_function_map(&function_map);

        subcontexts = InitializeSubcontexts();

        ActionManager& am = ActionManager::GetInstance();
        ServiceList& sm = ServiceList::GetInstance();

        LoadBootScripts(am, sm); // 加载启动脚本

        // Turning this on and letting the INFO logging be discarded adds 0.2s to
        // Nexus 9 boot time, so it's disabled by default.
        if (false) DumpState();

        am.QueueEventTrigger("early-init");

        // Queue an action that waits for coldboot done so we know ueventd has set up all of /dev...
        am.QueueBuiltinAction(wait_for_coldboot_done_action, "wait_for_coldboot_done");
        // ... so that we can start queuing up actions that require stuff from /dev.
        am.QueueBuiltinAction(MixHwrngIntoLinuxRngAction, "MixHwrngIntoLinuxRng");
        am.QueueBuiltinAction(SetMmapRndBitsAction, "SetMmapRndBits");
        am.QueueBuiltinAction(SetKptrRestrictAction, "SetKptrRestrict");
        Keychords keychords;
        am.QueueBuiltinAction(
            [&epoll, &keychords](const BuiltinArguments& args) -> Result<Success> {
                for (const auto& svc : ServiceList::GetInstance()) {
                    keychords.Register(svc->keycodes());
                }
                keychords.Start(&epoll, HandleKeychord);
                return Success();
            },
            "KeychordInit");
        am.QueueBuiltinAction(console_init_action, "console_init");

        // Trigger all the boot actions to get us started.
        am.QueueEventTrigger("init");

        // Repeat mix_hwrng_into_linux_rng in case /dev/hw_random or /dev/random
        // wasn't ready immediately after wait_for_coldboot_done
        am.QueueBuiltinAction(MixHwrngIntoLinuxRngAction, "MixHwrngIntoLinuxRng");

        // Initialize binder before bringing up other system services
        am.QueueBuiltinAction(InitBinder, "InitBinder");

        // Don't mount filesystems or start core system services in charger mode.
        std::string bootmode = GetProperty("ro.bootmode", "");
        if (bootmode == "charger") {
            am.QueueEventTrigger("charger");
        } else {
            am.QueueEventTrigger("late-init");
        }

        // Run all property triggers based on current state of the properties.
        am.QueueBuiltinAction(queue_property_triggers_action, "queue_property_triggers");

        while (true) {
            // By default, sleep until something happens.
            auto epoll_timeout = std::optional<std::chrono::milliseconds>{};

            if (do_shutdown && !shutting_down) {
                do_shutdown = false;
                if (HandlePowerctlMessage(shutdown_command)) {
                    shutting_down = true;
                }
            }

            if (!(waiting_for_prop || Service::is_exec_service_running())) {
                am.ExecuteOneCommand();
            }
            if (!(waiting_for_prop || Service::is_exec_service_running())) {
                if (!shutting_down) {
                    auto next_process_action_time = HandleProcessActions();

                    // If there's a process that needs restarting, wake up in time for that.
                    if (next_process_action_time) {
                        epoll_timeout = std::chrono::ceil<std::chrono::milliseconds>(
                                *next_process_action_time - boot_clock::now());
                        if (*epoll_timeout < 0ms) epoll_timeout = 0ms;
                    }
                }

                // If there's more work to do, wake up again immediately.
                if (am.HasMoreCommands()) epoll_timeout = 0ms;
            }

            if (auto result = epoll.Wait(epoll_timeout); !result) {
                LOG(ERROR) << result.error();
            }
        }

        return 0;
    }
```



