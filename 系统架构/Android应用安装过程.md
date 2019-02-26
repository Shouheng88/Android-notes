# Android 系统源码源码-应用安装过程

Android 中应用安装的过程就是解析 AndroidManifest.xml 的过程，系统可以从 Manifest 中得到应用程序的相关信息，比如 Activity、Service、Broadcast Receiver 和 ContentProvider 等。这些工作都是由 PackageManageService 负责的，也就是所谓的 PMS. 它跟 AMS 一样都是一种远程的服务，并且都是在系统启动 SystemServer 的时候启动的。下面我们通过源代码来分析下这个过程。

## 1、启动 PMS 的过程

系统在启动 SystemServer 的过程会启动 PMS，系统的启动过程可以参考下面这篇文章学习，

[Android 系统源码-1：Android 系统启动流程源码分析](https://juejin.im/post/5c4471e56fb9a04a027aa8ac)

在启动 SystemServer 的时候会调用 `startBootstrapServices()` 方法启动引导服务。PMS 就是在这个方法中启动的，

```java
    private void startBootstrapServices() {
        // ...
        mPackageManagerService = PackageManagerService.main(mSystemContext, installer,
                mFactoryTestMode != FactoryTest.FACTORY_TEST_OFF, mOnlyCore);
        mFirstBoot = mPackageManagerService.isFirstBoot();
        mPackageManager = mSystemContext.getPackageManager();
        // ...
    }
```

可以看出，系统是通过调用 PMS 的 main 方法来将其启动起来的。其 main 方法会先实例化一个 PMS 对象，然后调用 ServiceManager 的静态方法将其注册到 ServiceManager 中进行管理。

```java
    public static PackageManagerService main(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
        PackageManagerServiceCompilerMapping.checkProperties();

        PackageManagerService m = new PackageManagerService(context, installer,
                factoryTest, onlyCore);
        m.enableSystemUserPackages();
        ServiceManager.addService("package", m);
        final PackageManagerNative pmn = m.new PackageManagerNative();
        ServiceManager.addService("package_native", pmn);
        return m;
    }
```

当我们需要使用 PMS 解析 APK 的时候就会从 ServiceManager 中获取。

在 PMS 的构造方法中有许多工作要完成。一个 APK 安装的主要分成下面几个步骤，

1. **拷贝文件到指定的目录**：默认情况下，用户安装的 APK 首先会被拷贝到 `/data/app` 目录下，`/data/app` 目录是用户有权限访问的目录，在安装 APK 的时候会自动选择该目录存放用户安装的文件，而系统的 APK 文件则被放到了 `/system` 分区下，包括 `/system/app`，`/system/vendor/app`，以及 `/system/priv-app` 等等，该分区只有 ROOT 权限的用户才能访问，这也就是为什么在没有 Root 手机之前，我们没法删除系统出场的 APP 的原因了。
2. **解压缩 APK，拷贝文件，创建应用的数据目录**：为了加快 APP 的启动速度，APK 在安装的时候，会首先将 APP 的可执行文件 dex 拷贝到 `/data/dalvik-cache` 目录，缓存起来。然后，在 `/data/data/` 目录下创建应用程序的数据目录 (以应用的包名命名)，存放在应用的相关数据，如数据库、XML 文件、Cache、二进制的 so 动态库等。
3. 解析 APK 的 AndroidManifest.xml 文件。

```java
    public PackageManagerService(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
        // ....

        synchronized (mInstallLock) {
        synchronized (mPackages) {
            // Expose private service for system components to use.
            LocalServices.addService(
                    PackageManagerInternal.class, new PackageManagerInternalImpl());
            sUserManager = new UserManagerService(context, this,
                    new UserDataPreparer(mInsstaller, mInstallLock, mContext, mOnlyCore), mPackages);
            mPermissionManager = PermissionManagerService.create(context,
                    new DefaultPermissionGrantedCallback() {
                        @Override
                        public void onDefaultRuntimePermissionsGranted(int userId) {
                            synchronized(mPackages) {
                                mSettings.onDefaultRuntimePermissionsGrantedLPr(userId);
                            }
                        }
                    }, mPackages /*externalLock*/);
            mDefaultPermissionPolicy = mPermissionManager.getDefaultPermissionGrantPolicy();
            mSettings = new Settings(mPermissionManager.getPermissionSettings(), mPackages);
        }
        }
        // ...

        mPackageDexOptimizer = new PackageDexOptimizer(installer, mInstallLock, context, "*dexopt*");
        DexManager.Listener dexManagerListener = DexLogger.getListener(this, installer, mInstallLock);
        mDexManager = new DexManager(mContext, this, mPackageDexOptimizer, installer, mInstallLock, dexManagerListener);
        mArtManagerService = new ArtManagerService(mContext, this, installer, mInstallLock);

        // ...

        synchronized (mInstallLock) {
        synchronized (mPackages) {
            // 创建消息
            mHandlerThread = new ServiceThread(TAG,
                    Process.THREAD_PRIORITY_BACKGROUND, true /*allowIo*/);
            mHandlerThread.start();
            mHandler = new PackageHandler(mHandlerThread.getLooper());
            // ...

            // 扫描各个目录获取 APK 文件：VENDOR_OVERLAY_DIR           
            // framework 文件夹：frameworkDir
            // 系统文件夹：privilegedAppDir systemAppDir
            // 供应商的包：Environment.getVendorDirectory()
            // 原始设备制造商的包 ：Environment.getOdmDirectory()
            // 原始设计商的包：Environment.getOdmDirectory()
            // 原始产品的包：

            // ....

            mInstallerService = new PackageInstallerService(context, this);
            final Pair<ComponentName, String> instantAppResolverComponent = getInstantAppResolverLPr();
            if (instantAppResolverComponent != null) {
                mInstantAppResolverConnection = new InstantAppResolverConnection(
                        mContext, instantAppResolverComponent.first,
                        instantAppResolverComponent.second);
                mInstantAppResolverSettingsComponent =
                        getInstantAppResolverSettingsLPr(instantAppResolverComponent.first);
            } else {
                mInstantAppResolverConnection = null;
                mInstantAppResolverSettingsComponent = null;
            }
            updateInstantAppInstallerLocked(null);

            final Map<Integer, List<PackageInfo>> userPackages = new HashMap<>();
            final int[] currentUserIds = UserManagerService.getInstance().getUserIds();
            for (int userId : currentUserIds) {
                userPackages.put(userId, getInstalledPackages(/*flags*/ 0, userId).getList());
            }
            mDexManager.load(userPackages);
        } // synchronized (mPackages)
        } // synchronized (mInstallLock)

        // ....
    }
```

在构造方法中会扫描多个目录来获取 APK 文件，上述注释中我们已经给出了这些目录，及其获取的方式。当扫描一个路径的时候会使用 `scanDirLI()` 方法来完成扫描工作。

```java
    private void scanDirLI(File scanDir, int parseFlags, int scanFlags, long currentTime) {
        final File[] files = scanDir.listFiles();
        if (ArrayUtils.isEmpty(files)) {
            return;
        }

        try (ParallelPackageParser parallelPackageParser = new ParallelPackageParser(
                mSeparateProcesses, mOnlyCore, mMetrics, mCacheDir, mParallelPackageParserCallback)) {
            int fileCount = 0;
            for (File file : files) {
                final boolean isPackage = (isApkFile(file) || file.isDirectory())
                        && !PackageInstallerService.isStageName(file.getName());
                if (!isPackage) {
                    continue;
                }
                // 提交文件用来解析
                parallelPackageParser.submit(file, parseFlags);
                fileCount++;
            }

            for (; fileCount > 0; fileCount--) {
                // 获取解析的结果，即从队列阻塞队列中获取解析的结果
                ParallelPackageParser.ParseResult parseResult = parallelPackageParser.take();
                // ...
                if (throwable == null) {
                    // TODO(toddke): move lower in the scan chain
                    // Static shared libraries have synthetic package names
                    if (parseResult.pkg.applicationInfo.isStaticSharedLibrary()) {
                        renameStaticSharedLibraryPackage(parseResult.pkg);
                    }
                    try {
                        if (errorCode == PackageManager.INSTALL_SUCCEEDED) {
                            scanPackageChildLI(parseResult.pkg, parseFlags, scanFlags, currentTime, null);
                        }
                    } catch (PackageManagerException e) {
                        errorCode = e.error;
                    }
                }
                // 。。。
            }
        }
    }
```

从上面的代码中可以看出，提交文件来解析以及获取解析都是通过 ParallelPackageParser 来完成的。它使用 `submit()` 方法来提交文件用来解析，使用 `take()` 方法获取解析的结果。这两个方法的定义如下，

```java
    public void submit(File scanFile, int parseFlags) {
        mService.submit(() -> {
            ParseResult pr = new ParseResult();
            try {
                PackageParser pp = new PackageParser();
                pp.setSeparateProcesses(mSeparateProcesses);
                pp.setOnlyCoreApps(mOnlyCore);
                pp.setDisplayMetrics(mMetrics);
                pp.setCacheDir(mCacheDir);
                pp.setCallback(mPackageParserCallback);
                pr.scanFile = scanFile;
                pr.pkg = parsePackage(pp, scanFile, parseFlags);
            } catch (Throwable e) {
                pr.throwable = e;
            }
            try {
                mQueue.put(pr);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mInterruptedInThread = Thread.currentThread().getName();
            }
        });
    }

    public ParseResult take() {
        try {
            if (mInterruptedInThread != null) {
                throw new InterruptedException("Interrupted in " + mInterruptedInThread);
            }
            return mQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
```

`submit()` 方法使用一个线程池来执行任务，也就是上面的 mService。它会将要解析的信息封装成 PackageParser 对象，然后把解析的结果信息封装成 ParseResult 放进一个阻塞队列中。当调用 `take()` 方法的时候会从该阻塞队列中获取解析的结果。

包信息的解析最终是通过 PackageParser 的 `parsePackage()` 方法来完成的。其定义如下，

```java
    public Package parsePackage(File packageFile, int flags, boolean useCaches)
            throws PackageParserException {
        Package parsed = useCaches ? getCachedResult(packageFile, flags) : null;
        if (parsed != null) {
            return parsed;
        }

        long parseTime = LOG_PARSE_TIMINGS ? SystemClock.uptimeMillis() : 0;
        if (packageFile.isDirectory()) {
            parsed = parseClusterPackage(packageFile, flags);
        } else {
            // 是文件，所以走这条路线
            parsed = parseMonolithicPackage(packageFile, flags);
        }

        long cacheTime = LOG_PARSE_TIMINGS ? SystemClock.uptimeMillis() : 0;
        cacheResult(packageFile, flags, parsed);
        return parsed;
    }

```

我们会在这方法中进入到 `parseMonolithicPackage()` 来对文件进行解析。

```java
    public Package parseMonolithicPackage(File apkFile, int flags) throws PackageParserException {
        final PackageLite lite = parseMonolithicPackageLite(apkFile, flags);
        final SplitAssetLoader assetLoader = new DefaultSplitAssetLoader(lite, flags);
        try {
            // 解析
            final Package pkg = parseBaseApk(apkFile, assetLoader.getBaseAssetManager(), flags);
            pkg.setCodePath(apkFile.getCanonicalPath());
            pkg.setUse32bitAbi(lite.use32bitAbi);
            return pkg;
        } catch (IOException e) {
            throw new PackageParserException(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION);
        } finally {
            IoUtils.closeQuietly(assetLoader);
        }
    }
```

在这个方法中会使用 `parseBaseApk()` 来对 APK 文件进行解析，

```java
    private Package parseBaseApk(File apkFile, AssetManager assets, int flags)
            throws PackageParserException {
        final String apkPath = apkFile.getAbsolutePath();

        String volumeUuid = null;
        if (apkPath.startsWith(MNT_EXPAND)) {
            final int end = apkPath.indexOf('/', MNT_EXPAND.length());
            volumeUuid = apkPath.substring(MNT_EXPAND.length(), end);
        }

        mParseError = PackageManager.INSTALL_SUCCEEDED;
        mArchiveSourcePath = apkFile.getAbsolutePath();

        XmlResourceParser parser = null;
        try {
            final int cookie = assets.findCookieForPath(apkPath);
            // 读取 AndroidManifest.xml
            parser = assets.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);
            final Resources res = new Resources(assets, mMetrics, null);

            final String[] outError = new String[1];
            // 在这里进一步解析 Manifest 的各种信息
            final Package pkg = parseBaseApk(apkPath, res, parser, flags, outError);
   
            pkg.setVolumeUuid(volumeUuid);
            pkg.setApplicationVolumeUuid(volumeUuid);
            pkg.setBaseCodePath(apkPath);
            pkg.setSigningDetails(SigningDetails.UNKNOWN);
            return pkg;
        } catch (PackageParserException e) {
            throw e;
        } catch (Exception e) {
            throw new PackageParserException(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION);
        } finally {
            IoUtils.closeQuietly(parser);
        }
    }
```

这里的 ANDROID_MANIFEST_FILENAME 是一个字符串，这个字符串的定义是 AndroidManifest.xml，所以，我们找到了解析 Manifest 的地方。

然后方法会进入到 `parseBaseApk()` 方法中进一步对 Manifest 进行解析。其读取操作就是基本的 XML 解析的过程。它会使用内部定义的字符串常量从 Manifest 中获取应用的版本还有四大组件等信息。

解析完了 APK 之后会一路经过 return 语句返回到 `scanDirLI()` 方法中，当从阻塞队列中取出 Package 之后将会调用 `scanPackageChildLI()` 在该方法中会将解析的出的 APK 信息缓存到 PMS 中。

这样，在系统启动之后 PMS 就解析了全部的 APK 文件，并将其缓存到了 PMS 中。这样这些应用程序还无法展示给用户，所以需要 Launcher 桌面程序从 PMS 中获取安装包信息并展示到桌面上。

## 2、应用安装的过程

虽然 PMS 用来负责应用的安装和卸载，但是真实的工作却是交给 installd 来实现的。 installd 是在系统启动的时候，由 init 进程解析 init.rc 文件创建的。在早期版本的 Android 中，它使用 Socket 与 Java 层的 Installer 进行通信。在 9.0 的代码中，它使用 Binder 与 Java 层的 Installer 进行通信。当启动 Installd 的时候，将会调用其 main 方法，

```c++
int main(const int argc, char *argv[]) {
    return android::installd::installd_main(argc, argv);
}

static int installd_main(const int argc ATTRIBUTE_UNUSED, char *argv[]) {
    int ret;
    int selinux_enabled = (is_selinux_enabled() > 0);

    setenv("ANDROID_LOG_TAGS", "*:v", 1);
    android::base::InitLogging(argv);

    SLOGI("installd firing up");

    union selinux_callback cb;
    cb.func_log = log_callback;
    selinux_set_callback(SELINUX_CB_LOG, cb);

    // 初始化全局信息
    if (!initialize_globals()) {
        exit(1);
    }

    // 初始化相关目录
    if (initialize_directories() < 0) {
        exit(1);
    }

    if (selinux_enabled && selinux_status_open(true) < 0) {
        exit(1);
    }

    if ((ret = InstalldNativeService::start()) != android::OK) {
        exit(1);
    }

    // 加入到 Binder 线程池当中
    IPCThreadState::self()->joinThreadPool();

    LOG(INFO) << "installd shutting down";

    return 0;
}
```

在启动 Installd 的时候会初始化各种相关的目录，这部分内容就不展开了。然后，它会调用 `IPCThreadState::self()->joinThreadPool()` 一行来将当前线程池加入到 Binder 线程池当中等待通信。

当 Java 层的 Installer 需要与之通信的时候，会调用 `connect()` 方法与之建立联系。其源码如下，这里会通过 ServiceManager 获取 installd 服务，然后将其转换成本地的服务进行 IPC 的调用。

```java
    private void connect() {
        // 获取远程服务 
        IBinder binder = ServiceManager.getService("installd");
        if (binder != null) {
            try {
                binder.linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        connect();
                    }
                }, 0);
            } catch (RemoteException e) {
                binder = null;
            }
        }

        if (binder != null) {
            // 转成本地服务进行 IPC 调用
            mInstalld = IInstalld.Stub.asInterface(binder);
            try {
                invalidateMounts();
            } catch (InstallerException ignored) {
            }
        } else {
            // 重连
            BackgroundThread.getHandler().postDelayed(() -> {
                connect();
            }, DateUtils.SECOND_IN_MILLIS);
        }
    }
```

Installer 与 PMC 类似，也是一种系统服务，它的启动的时刻与 PMS 基本一致，位于同一个方法中，并且其启动时刻位于 PMS 之前。

## 2、从 ADB 安装的过程




