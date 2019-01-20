# Android 系统启动过程

现在我们来梳理下 Android 系统的启动过程。Android 启动过程还是比较重要的，因为在这个过程中除了要完成 Linux 系统的初始化工作还要完成 Android 的基础服务和启动界面的初始化工作。

在这篇文章中，我们不打算过多深入源码。因为 Android 中任何一个功能模块在 Framework 层都涉及大量的代码调用。过多深入源码只会让我们迷失在一层层的调用栈中。相比之下，我更倾向于只出一些核心代码，另外梳理下调用栈的流程。当我们需要深入研究这方面的内容的时候，知道去哪里找答案就够了。

## 1、系统启动

按下电源之后，首先加载引导程序 BootLoader 到 RAM；然后，执行引导程序 BootLoader 以把系统 OS 拉起来；接着，启动 Linux 内核；内核中启动的第一个用户进程是 init 进程，init 进程会通过解析 init.rc 来启动 zygote 服务；Zygote 又会进一步的启动 SystemServer；在 SystemServer 中，Android 会启动一系列的系统服务供用户调用。

Android 系统中 init 程序对应的 `Android.mk` 位于 `system/core/init/Android.mk`，是一种 Makefile 文件，用来向编译系统描述我们的源代码。我们可以使用 make 工具来执行该文件。所以，mk 文件就像是 Shell 脚本一样。

### 1.1 执行 init 程序

Linux 内核加载完成后，首先启动 init 进程。在 8.0 的源码中系统启动的第一个阶段是创建启动所需的各种目录。而在最新的源码中，这部分代码被包含在了 `init_first_stage` 中：

```C++
    // platform/system/core/init/init_first_stage.cpp
    int main(int argc, char** argv) {
        if (REBOOT_BOOTLOADER_ON_PANIC) {
            InstallRebootSignalHandlers();
        }
        boot_clock::time_point start_time = boot_clock::now();
        std::vector<std::pair<std::string, int>> errors;
    #define CHECKCALL(x) \
        if (x != 0) errors.emplace_back(#x " failed", errno);
        umask(0);
        CHECKCALL(clearenv());
        CHECKCALL(setenv("PATH", _PATH_DEFPATH, 1));
        // 创建目录
        CHECKCALL(mount("tmpfs", "/dev", "tmpfs", MS_NOSUID, "mode=0755"));
        CHECKCALL(mkdir("/dev/pts", 0755));
        CHECKCALL(mkdir("/dev/socket", 0755));
        // ...
    #undef CHECKCALL
        auto reboot_bootloader = [](const char*) { RebootSystem(ANDROID_RB_RESTART2, "bootloader"); };
        InitKernelLogging(argv, reboot_bootloader);
        // ...
        const char* path = "/system/bin/init";
        const char* args[] = {path, nullptr};
        execv(path, const_cast<char**>(args));
        return 1;
    }
```

在系统启动过程中会多次调用 `execv()`，每次调用该方法时会重新执行 main() 方法。该方法如下：

```
int execv(const char *progname, char *const argv[]);   //#include <unistd.h>
```

execv() 会停止执行当前的进程，并且以 progname 应用进程替换被停止执行的进程，进程 ID 不会改变。

然后是 `init.cpp` 进程的入口函数 main:

```C++
    // platform/system/core/init/init.cpp
    int main(int argc, char** argv) {
        if (!strcmp(basename(argv[0]), "ueventd")) {
            return ueventd_main(argc, argv);
        }
        if (argc > 1 && !strcmp(argv[1], "subcontext")) {
            android::base::InitLogging(argv, &android::base::KernelLogger);
            const BuiltinFunctionMap function_map;
            return SubcontextMain(argc, argv, &function_map);
        }
        if (REBOOT_BOOTLOADER_ON_PANIC) {
            // 初始化重启系统的处理信号
            InstallRebootSignalHandlers();
        }
        // ...
        property_init(); // 初始化属性服务
        // ...
        Epoll epoll; // 创建 epoll 句柄
        if (auto result = epoll.Open(); !result) {
            PLOG(FATAL) << result.error();
        }

        InstallSignalFdHandler(&epoll);
        // ...
        StartPropertyService(&epoll); // 启动属性服务
        // ...

        ActionManager& am = ActionManager::GetInstance();
        ServiceList& sm = ServiceList::GetInstance();

        LoadBootScripts(am, sm); // 加载启动脚本
        // ...
        // 充电模式不启动系统，否则启动系统
        std::string bootmode = GetProperty("ro.bootmode", "");
        if (bootmode == "charger") {
            am.QueueEventTrigger("charger");
        } else {
            am.QueueEventTrigger("late-init");
        }
        // ...
        return 0;
    }
```

这里会在 `LoadBootScripts()` 方法中解析 `init.rc` 文件。关于该文件指令的含义可以参考 AOSP 中的文档：[《Android Init Language》](https://chromium.googlesource.com/aosp/platform/system/core/+/refs/heads/master/init/). 完成解析相关的类是 `ActionManager`、`Parser` 和 `XXParser`，均位于 `system/core/init` 目录下面。除此之外，还有 `Action` 和 `Service` 等类。它们的作用是，各种 `Parser` 用来解析 rc 文件中的指令。解析出的指令会被封装成 `Action` 和 `Service` 等对象。

打开该文件我们可以看到其中包含了下面两行代码，这里使用了占位符，也就是说，它会根据当前的环境变量加载当前目录下对应的文件。并且，我们可以看到在 `system/core/rootdir` 目录下面确实存在着 `init.zygote64.rc` 和 `init.zygote32.rc` 等文件。

```c
import /init.${ro.hardware}.rc
import /init.${ro.zygote}.rc
```

以 `rinit.zygote64.rc` 为例，它表示通知 init 进程创建名为 zygote 的进程。执行路径是 `/system/bin/app_process64`，

```c
// platform/system/core/rootdir/init.zygote64.rc
service zygote /system/bin/app_process64 -Xzygote /system/bin --zygote --start-system-server
    class main
    // ...
```

我们可以看出它使用了 service 指令，所以它将被解析成 Service. 

注意到在 `init.cpp` 的 main() 方法的最后，如果非充电模式将触发 `late-init`. 在 `rc` 中配置了对 `late-init` 事件的监听，通过 `on` 来实现的。同时，它又使用 `trigger` 触发了其他的命令。这些命令也都是通过 `on` 来监听的。（当然，rc 只是一种配置文件，而实际的逻辑是被解析之后在程序中完成的。）

在 `late-init` 事件触发的事件当中包含了 `zygote-start` 事件. 而 `zygote-start` 监听实现又根据监听条件又多种。不过，它们都会调用 `start zygote` 方法。这里的 start 会被映射到 builtins 类的 `do_start()` 方法。该方法会调用 Service 的 `start()` 方法。该方法主要是调用 clone 或 fork 创建子进程，然后调用 execve 执行配置的二进制文件，另外根据之前在 rc 文件中的配置，去执行这些配置。因此程序将开始执行 app_process64. 

```c++
// platform/system/core/init/service.cpp
Result<Success> Service::Start() {
    // ...
    pid_t pid = -1;
    if (namespace_flags_) {
        pid = clone(nullptr, nullptr, namespace_flags_ | SIGCHLD, nullptr);
    } else {
        pid = fork();
    }

    if (pid == 0) {
        umask(077);
        // ...
        // 内部调用 execv() 来执行
        if (!ExpandArgsAndExecv(args_, sigstop_)) {
            PLOG(ERROR) << "cannot execve('" << args_[0] << "')";
        }
        _exit(127);
    }
    // ...
    return Success();
}
```

> 映射关系参考源码：system/core/init/builtins.cpp    
> 关于 rc 文件的命令的解析，可以参考[《Android 8.0 系统启动流程之init.rc解析与service流程(七)》](https://blog.csdn.net/marshal_zsx/article/details/80600622)

上述 rc 文件的 `/system/bin/app_process64` 对应的 mk 文件位于 `/base/cmds/app_process/Android.mk` 目录下面。从该文件中我们可以看出，不论 app_process、app_process32 还是 app_process64，对应的源文件都是 `app_main.cpp`. 于是程序将进入 `app_main.cpp` 的 main() 方法。

进入 main() 方法之后先要进行指令的参数的解析，

```c++
// platform/frameworks/base/cmds/app_process/app_main.cpp
int main(int argc, char* const argv[])
{
    // ...
    bool zygote = false;
    bool startSystemServer = false;
    bool application = false;
    String8 niceName;
    String8 className;

    ++i;  // Skip unused "parent dir" argument.
    while (i < argc) {
        const char* arg = argv[i++];
        if (strcmp(arg, "--zygote") == 0) {
            zygote = true;
            niceName = ZYGOTE_NICE_NAME;
        } else if (strcmp(arg, "--start-system-server") == 0) {
            startSystemServer = true;
        } else if (strcmp(arg, "--application") == 0) {
            application = true;
        } else if (strncmp(arg, "--nice-name=", 12) == 0) {
            niceName.setTo(arg + 12);
        } else if (strncmp(arg, "--", 2) != 0) {
            className.setTo(arg);
            break;
        } else {
            --i;
            break;
        }
    }
    // ...
    if (zygote) {
        runtime.start("com.android.internal.os.ZygoteInit", args, zygote);
    } else if (className) {
        runtime.start("com.android.internal.os.RuntimeInit", args, zygote);
    } else {
        app_usage();
    }
}
```

我们从之前的 rc 文件中可以看出，参数为 `--zygote`，因此将调用 `ZygoteInit` 的 main() 方法继续执行。**这里的 runtime 是 `AndroidRuntime`，这里的 `start()` 方法是一种 JNI 调用。这里将会调用 Java 中的静态 main() 方法继续执行。** 这种调用方式还是比较重要的，我们经常在 Java 中调用 C++ 的方法，而这里是在 C++ 中调用 Java 的方法。它的源码位于 `base\core\jni\AndroidRuntime.cpp`. 

```c++
// platform/frameworks/base/core/jni/AndroidRuntime.cpp
void AndroidRuntime::start(const char* className, const Vector<String8>& options, bool zygote)
{
    // ...

    // 获取ANDROID_ROOT环境变量
    const char* rootDir = getenv("ANDROID_ROOT");
    if (rootDir == NULL) {
        rootDir = "/system";
        if (!hasDir("/system")) {
            return;
        }
        setenv("ANDROID_ROOT", rootDir, 1);
    }

    // 启动虚拟机
    JniInvocation jni_invocation;
    jni_invocation.Init(NULL);
    JNIEnv* env;
    if (startVm(&mJavaVM, &env, zygote) != 0) {
        return;
    }
    onVmCreated(env);

    // ... 解析 main 函数以在下面进行触发

    // 启动线程，当前线程将会变成虚拟机的主线程，并且直到虚拟机退出的时候才结束。
    char* slashClassName = toSlashClassName(className != NULL ? className : "");
    jclass startClass = env->FindClass(slashClassName);
    if (startClass == NULL) {
        ALOGE("JavaVM unable to locate class '%s'\n", slashClassName);
    } else {
        jmethodID startMeth = env->GetStaticMethodID(startClass, "main",
            "([Ljava/lang/String;)V");
        if (startMeth == NULL) {
            ALOGE("JavaVM unable to find main() in '%s'\n", className);
        } else {
            env->CallStaticVoidMethod(startClass, startMeth, strArray);
        }
    }
    // ...
}
```

在上面的方法中，我们可以看出启动虚拟机的时候需要调用 `startVM()` 方法来启动。当虚拟机启动完成之后使用句柄函数 env 来执行 ZygoteInit 的静态 `main()` 方法。

### 1.2 启动 Zygote

根据上面的分析，系统已经启动了虚拟机。并且在虚拟机启动完成之后，程序进入了 `ZygoteInit` 中 `main()` 方法中，

```java
    // platform/framework/base/core/java/com/android/internal/os/ZygoteInit.java
    public static void main(String argv[]) {
        // ...
        try {
            // ...
            boolean startSystemServer = false;
            String socketName = "zygote";
            String abiList = null;
            boolean enableLazyPreload = false;
            for (int i = 1; i < argv.length; i++) {
                if ("start-system-server".equals(argv[i])) {
                    startSystemServer = true;
                } else if ("--enable-lazy-preload".equals(argv[i])) {
                    enableLazyPreload = true;
                } else if (argv[i].startsWith(ABI_LIST_ARG)) {
                    abiList = argv[i].substring(ABI_LIST_ARG.length());
                } else if (argv[i].startsWith(SOCKET_NAME_ARG)) {
                    socketName = argv[i].substring(SOCKET_NAME_ARG.length());
                } else {
                    throw new RuntimeException("Unknown command line argument: " + argv[i]);
                }
            }

            // 注册名为 zygote 的 Socket
            zygoteServer.registerServerSocketFromEnv(socketName);
            // 决定是否进行资源的预加载
            if (!enableLazyPreload) {
                // ... 记录日志信息
                preload(bootTimingsTraceLog);
                // ... 记录日志信息
            } else {
                Zygote.resetNicePriority();
            }

            gcAndFinalize(); // 进行 GC 清理空间

            // ...

            if (startSystemServer) {
                // 启动 SystemServer 进程，如果 r 为 null 则处于父进程，否则是子进程
                Runnable r = forkSystemServer(abiList, socketName, zygoteServer);
                if (r != null) {
                    r.run();
                    return;
                }
            }

            // 等待 AMS 连接请求
            caller = zygoteServer.runSelectLoop(abiList);
        } catch (Throwable ex) {
            throw ex;
        } finally {
            zygoteServer.closeServerSocket();
        }

        if (caller != null) {
            caller.run();
        }
    }
```
这里主要做了几件事情：

首先，创建 Server 端的 Socket. 这里创建的是 ZygoteServer 对象。它提供了等待 UNIX 套接字的命令，并且提供了 fork 虚拟机的方法。

然后，进行资源预加载。

接着，启动 SystemServer. 这里通过调用 forkSystemServer() 来进行。这里先会构建一个命令参数，然后调用 Zygote 的静态方法来 Fork 一个子进程。该方法内部又会调用 JNI 层的 `nativeForkSystemServer` 方法最终完成 Fork 操作。

```java
    // platform/framework/base/core/java/com/android/internal/os/Zygote.java
    private static Runnable forkSystemServer(String abiList, String socketName, ZygoteServer zygoteServer) {
        // ...

        /* 硬编码的命令行来启动 System Server */
        String args[] = {
            "--setuid=1000",
            "--setgid=1000",
            "--setgroups=1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1018,1021,1023,1024,1032,1065,3001,3002,3003,3006,3007,3009,3010",
            "--capabilities=" + capabilities + "," + capabilities,
            "--nice-name=system_server",
            "--runtime-args",
            "--target-sdk-version=" + VMRuntime.SDK_VERSION_CUR_DEVELOPMENT,
            "com.android.server.SystemServer",
        };

        ZygoteConnection.Arguments parsedArgs = null;
        int pid;
        try {
            parsedArgs = new ZygoteConnection.Arguments(args);
            ZygoteConnection.applyDebuggerSystemProperty(parsedArgs);
            ZygoteConnection.applyInvokeWithSystemProperty(parsedArgs);

            boolean profileSystemServer = SystemProperties.getBoolean(
                    "dalvik.vm.profilesystemserver", false);
            if (profileSystemServer) {
                parsedArgs.runtimeFlags |= Zygote.PROFILE_SYSTEM_SERVER;
            }

            /* 请求 fork System Server 进程 */
            pid = Zygote.forkSystemServer(
                    parsedArgs.uid, parsedArgs.gid,
                    parsedArgs.gids,
                    parsedArgs.runtimeFlags,
                    null,
                    parsedArgs.permittedCapabilities,
                    parsedArgs.effectiveCapabilities);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        }

        /* 对于子进行进行处理 */
        if (pid == 0) {
            if (hasSecondZygote(abiList)) {
                waitForSecondaryZygote(socketName);
            }

            zygoteServer.closeServerSocket();
            // 为新 fork 的 system server 进程停止剩下的工作
            return handleSystemServerProcess(parsedArgs);
        }

        return null;
    }
```

最后启动 select 循环，等待新的连接。下面是这个方法的定义，代码中的注释已经比较全了，我们就不多解释了。

```java
    // platform/framework/base/core/java/com/android/internal/os/ZygoteServer.java
    Runnable runSelectLoop(String abiList) {
        // ...
        while (true) { // 使用无限循环进行监听
            // ...
            for (int i = pollFds.length - 1; i >= 0; --i) {
                if ((pollFds[i].revents & POLLIN) == 0) {
                    continue;
                }
                if (i == 0) { // 遍历到最后一个
                    ZygoteConnection newPeer = acceptCommandPeer(abiList);
                    peers.add(newPeer);
                    fds.add(newPeer.getFileDesciptor());
                } else { // 正在等待连接
                    try {
                        ZygoteConnection connection = peers.get(i);
                         // processOneCommand() 从命令 socket 中读取一个命令，如果读取成功，将会fork子进程，并返回子进程的 main 方法. 如果是父进程，那么应该始终返回 null
                        final Runnable command = connection.processOneCommand(this);
                        if (mIsForkChild) {
                            // 子进程，需要至少一个命令
                            if (command == null) {
                                throw new IllegalStateException("command == null");
                            }
                            return command;
                        } else {
                            // server 进程，不应该存在要执行的命令
                            if (command != null) {
                                throw new IllegalStateException("command != null");
                            }
                            if (connection.isClosedByPeer()) { // 关闭请求
                                connection.closeSocket();
                                peers.remove(i);
                                fds.remove(i);
                            }
                        }
                    } catch (Exception e) {
                        if (!mIsForkChild) {
                            // 中间发生错误，关闭请求，告知请求端请求结束
                            ZygoteConnection conn = peers.remove(i);
                            conn.closeSocket();
                            fds.remove(i);
                        } else {
                            throw e;
                        }
                    } finally {
                        mIsForkChild = false;
                    }
                }
            }
        }
    }
```

当使用 `acceptCommandPeer()` 从 socket 中读取到了命令之后，会 fork 子进程并返回一个 Runnable，用来启动子进程的 main() 方法。这部分逻辑在 `acceptCommandPeer()` 方法中。它会调用 Zygote 类的静态方法 `forkAndSpecialize()` 来创建子进程。（与 SystemServer 进程创建时的静态方法不同）然后将调用 `handleChildProc()` 方法返回用来启动子进程的 main() 方法。其定义如下，

```java
    // platform/framework/base/core/java/com/android/internal/os/ZygoteConnection.java
    private Runnable handleChildProc(Arguments parsedArgs, FileDescriptor[] descriptors,
            FileDescriptor pipeFd, boolean isZygote) {
        // ...
        if (parsedArgs.invokeWith != null) {
            throw new IllegalStateException("WrapperInit.execApplication unexpectedly returned");
        } else {
            if (!isZygote) {
                return ZygoteInit.zygoteInit(parsedArgs.targetSdkVersion, parsedArgs.remainingArgs,
                        null /* classLoader */);
            } else {
                return ZygoteInit.childZygoteInit(parsedArgs.targetSdkVersion,
                        parsedArgs.remainingArgs, null /* classLoader */);
            }
        }
    }
```

这里的 isZygote 的含义是，是否以当前进程的子进程的形式来启动一个进程，使用 `--start-child-zygote` 参数来指定。因为当前我们启动的进程是父 Zygote 进程，所以将会调用 `ZygoteInit.zygoteInit()` 方法继续处理。该方法的核心代码只有两行，

```java
    // platform/framework/base/core/java/com/android/internal/os/ZygoteInit.java
    public static final Runnable zygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) {
        // ...
        ZygoteInit.nativeZygoteInit();
        return RuntimeInit.applicationInit(targetSdkVersion, argv, classLoader);
    }
```

`nativeZygoteInit()` 是一个 native 方法，用来启动 Binder 线程池。它对应的 native 方法定义在 `AndroidRuntime.cpp` 中。这里的 `gCurRuntime` 是 AppRumtime，定义在 `app_main.cpp` 中。

```c++
// platform/frameworks/base/cmds/app_process/app_main.cpp
static void com_android_internal_os_ZygoteInit_nativeZygoteInit(JNIEnv* env, jobject clazz)
{
    gCurRuntime->onZygoteInit();
}
```

`applicationInit()` 方法主要用来触发 SystemServer 的 main() 方法。在最新的代码中，会将要触发的方法和参数封装到一个 Runnable 中，并在它的 `run()` 方法中调用反射触发方法。所以，我们将进入 SystemServer 的 `main()` 方法。该类位于 `base\services\java\com\android\server` 下面。其方法定义如下，

```java
    // platform/frameworks/base/service/java/com/android/server/SystemServer.java
    public static void main(String[] args) {
        new SystemServer().run();
    }

    // platform/frameworks/base/service/java/com/android/server/SystemServer.java
    private void run() {
        try {
            // ...

            Looper.prepareMainLooper(); // 创建主线程消息循环
            System.loadLibrary("android_servers"); // 加载 so 库
            performPendingShutdown();
            // 创建系统的 context
            createSystemContext();
            // ServiceManager!!! 用来管理系统服务中的服务的创建、启动等生命周期
            mSystemServiceManager = new SystemServiceManager(mSystemContext);
            mSystemServiceManager.setStartInfo(mRuntimeRestart,
                    mRuntimeStartElapsedTime, mRuntimeStartUptime);
            LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);
            SystemServerInitThreadPool.get();
        } finally {
            traceEnd();  // InitBeforeStartServices
        }

        // 启动服务
        try {
            traceBeginAndSlog("StartServices");
            startBootstrapServices(); // 启动引导服务
            startCoreServices(); // 启动核心服务
            startOtherServices(); // 启动其他服务
            SystemServerInitThreadPool.shutdown();
        } catch (Throwable ex) {
            throw ex;
        } finally {
            traceEnd();
        }
        // ...
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }
```

从上面可以看出，这个方法中的主要逻辑是对系统中各种服务进行管理。创建了 `SystemServiceManager` 之后，借助它来实现对各种服务的创建、启动等生命周期进行管理。比如在 `startBootstrapServices()` 中会启动大名鼎鼎的 PMS 和 AMS 等. 启动服务的操作是通过调用 `SystemServiceManager` 的 `startService()` 方法完成的。该方法有 3 个重载的方法。但是，不论调用哪个方法，最终都会调用到下面的方法。

```java
    // platform/frameworks/base/services/core/com/android/server/SystemServiceManager.java
    public void startService(@NonNull final SystemService service) {
        mServices.add(service);
        long time = SystemClock.elapsedRealtime();
        try {
            service.onStart();
        } catch (RuntimeException ex) {
            throw new RuntimeException("Failed to start service " + service.getClass().getName()
                    + ": onStart threw an exception", ex);
        }
    }
```

在该方法中除了回调 service 的 `onStart()` 之外，还要将其注册到 `mServices` 中，它是 `ArrayList<SystemService>` 类型的变量，用来存储启动的服务。

此外，我们还注意到在 `run()` 方法中启动了一个 Looper 循环。这表明该系统服务主线程将会一直运行下去。关于 Looper 的内容可以参考我的另一篇文章：

[《Android 消息机制：Handler、MessageQueue 和 Looper》](https://blog.csdn.net/github_35186068/article/details/83718379)

### 1.3 启动 Launcher

系统启动过程中必不可少的一个环节就是启动 Launcher，就是所谓的 Android 桌面程序。在上面的方法中，系统会启动所需的各种服务，在其中的 `startOtherServices()` 方法中，会调用启动的服务的 `systemReady()` 方法来做系统启动准备就绪之后的逻辑。这其中就包括 AMS.  `startOtherServices()` 方法比较长，我们就不贴代码了。我们直接看下 AMS 的  `systemReady()` 方法。这个方法也比较长，我们只截取其中的一部分方法，

```java
    // platform/frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java
    public void systemReady(final Runnable goingCallback, TimingsTraceLog traceLog) {
        // ...
        synchronized (this) {
            // ...
            startHomeActivityLocked(currentUserId, "`");
            // ...
        }
    }
```

这里会调用 `startHomeActivityLocked()` 方法来继续操作以完成桌面的启动，

```java
    // platform/frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java
    boolean startHomeActivityLocked(int userId, String reason) {
        // ...
        // 构建一个用于启动桌面程序的 Intent，这个 Intent 包含一个 Category android.intent.category.HOME 类型的 Cateogry
        Intent intent = getHomeIntent();
        // 遍历安装包检查是否存在 Cateogry 为 android.intent.category.HOME 的 Activity
        ActivityInfo aInfo = resolveActivityInfo(intent, STOCK_PM_FLAGS, userId);
        if (aInfo != null) {
            // 将上述得到的应用信息传递给 Intent
            intent.setComponent(new ComponentName(aInfo.applicationInfo.packageName, aInfo.name));
            aInfo = new ActivityInfo(aInfo);
            aInfo.applicationInfo = getAppInfoForUser(aInfo.applicationInfo, userId);
            ProcessRecord app = getProcessRecordLocked(aInfo.processName,
                    aInfo.applicationInfo.uid, true);
            if (app == null || app.instr == null) {
                intent.setFlags(intent.getFlags() | FLAG_ACTIVITY_NEW_TASK);
                final int resolvedUserId = UserHandle.getUserId(aInfo.applicationInfo.uid);

                final String myReason = reason + ":" + userId + ":" + resolvedUserId;
                // 继续启动 Launcher 的进程
                mActivityStartController.startHomeActivity(intent, aInfo, myReason);
            }
        }
        return true;
    }
```

然后方法将进入 ActivityStartController 的 `startHomeActivity()` 方法继续进行，

```java
    // platform/frameworks/base/services/core/java/com/android/server/am/ActivityStartController.java
    void startHomeActivity(Intent intent, ActivityInfo aInfo, String reason) {
        // 把 Launcher 的堆栈移到顶部
        mSupervisor.moveHomeStackTaskToTop(reason);
        // obtainStarter() 将返回一个 ActivityStarter，然后调用它的 execute() 继续处理
        mLastHomeActivityStartResult = obtainStarter(intent, "startHomeActivity: " + reason)
                .setOutActivity(tmpOutRecord)
                .setCallingUid(0)
                .setActivityInfo(aInfo)
                .execute();
        mLastHomeActivityStartRecord = tmpOutRecord[0];
        if (mSupervisor.inResumeTopActivity) {
            mSupervisor.scheduleResumeTopActivities();
        }
    }
```

这里通过 `obtainStarter()` 将返回一个 ActivityStarter，然后调用它的 execute() 继续处理，显然这里使用的是构建者设计模式。剩下的流程就是 Activity 的启动流程。我们不做更多说明了，可以在随后介绍 Activity 启动的时候来继续梳理。

## 2、总结

上面我们梳理了 Android 系统启动的主流程，这里我们总结一下。

![系统启动流程](res/launcher.png)

### 推荐资料：

1. [Android 8.0 系统启动流程之zygote进程(八)](https://blog.csdn.net/marshal_zsx/article/details/80547780)
2. [Android 8.0 系统启动流程之init.rc解析与service流程(七)](https://blog.csdn.net/marshal_zsx/article/details/80600622)