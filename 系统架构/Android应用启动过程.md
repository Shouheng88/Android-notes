# Android 应用启动过程

在之前的文中，我们已经了解过了 Android 系统启动的过程。系统启动之后会由 PMS 安装系统应用，并启动 Launcher，也就是桌面程序。然后，我们安装的程序的图标将会显示到桌面上面。

所谓应用启动过程分成两种情形，一个是应用进程已经建立，一种是应用进程没有建立的情况下。后者需要先创建应用进程，然后再执行启动的过程。

安卓系统中的应用在源码中的位置是 `platform/packages/apps`。这里我们以 Launcher3 为例，它的 Launcher 类也就是我们通常所说的 Main Activity. 当系统启动的时候会由它来展示我们安装的各种应用。

当我们点击应用的图标的时候将会启动应用，它先以 `Intent.FLAG_ACTIVITY_NEW_TASK` 构建一个 Intent 来启动 Activity. 随后的过程就与启动一个普通的 Activity 差不多（调用 Activity 的 `startActivity()` 方法），只是当应用进程不存在的情况下，需要先创建应用进程。

## 1、应用进程启动的过程

从之前的文中，我们知道系统启动的时候会创建一个 Server 端的 Socket 等待 AMS 请求 Zygote 通过 fork 来创建应用进程。当 AMS 需要启动应用进程的时候，它将会调用下面的方法，

```java
    // platform\framework\base\services\core\java\com\android\server\am\ActivityManagerService.java
    private ProcessStartResult startProcess(String hostingType, String entryPoint,
            ProcessRecord app, int uid, int[] gids, int runtimeFlags, int mountExternal,
            String seInfo, String requiredAbi, String instructionSet, String invokeWith,
            long startTime) {
        try {
            final ProcessStartResult startResult;
            if (hostingType.equals("webview_service")) {
                startResult = startWebView(entryPoint,
                        app.processName, uid, uid, gids, runtimeFlags, mountExternal,
                        app.info.targetSdkVersion, seInfo, requiredAbi, instructionSet,
                        app.info.dataDir, null,
                        new String[] {PROC_START_SEQ_IDENT + app.startSeq});
            } else {
                startResult = Process.start(entryPoint,
                        app.processName, uid, uid, gids, runtimeFlags, mountExternal,
                        app.info.targetSdkVersion, seInfo, requiredAbi, instructionSet,
                        app.info.dataDir, invokeWith,
                        new String[] {PROC_START_SEQ_IDENT + app.startSeq});
            }
            return startResult;
        }
    }
```

当然，方法名称和调用的位置可能因为源码的版本不同而不同。但它们本质上都是通过调用 `Process` 的 `start()` 方法来最终启动应用进程的。

方法的调用会通过 `Process` 的 `start()` 方法直到 ZygoteProcess 的 `startViaZygote()`. 因为调用链比较简单，所以我们直接给出下面的方法即可，

```java
    // platform\framework\base\core\java\android\os\ZygoteProcess.java
    private Process.ProcessStartResult startViaZygote(/* 各种参数 */)
            throws ZygoteStartFailedEx {
        ArrayList<String> argsForZygote = new ArrayList<String>();

        // --runtime-args, --setuid=, --setgid=,
        // and --setgroups= must go first
        argsForZygote.add("--runtime-args");
        argsForZygote.add("--setuid=" + uid);
        argsForZygote.add("--setgid=" + gid);
        argsForZygote.add("--runtime-flags=" + runtimeFlags);
        if (mountExternal == Zygote.MOUNT_EXTERNAL_DEFAULT) {
            argsForZygote.add("--mount-external-default");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_READ) {
            argsForZygote.add("--mount-external-read");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_WRITE) {
            argsForZygote.add("--mount-external-write");
        }
        argsForZygote.add("--target-sdk-version=" + targetSdkVersion);

        // ... 准备各种参数

        synchronized(mLock) {
            return zygoteSendArgsAndGetResult(openZygoteSocketIfNeeded(abi), argsForZygote);
        }
    }
```

可以看到，这里准备了一些用于 Zygote 的参数，然后调用了 `zygoteSendArgsAndGetResult()` 方法来向 Zygote 发送请求并获取返回结果。这个方法中要求输入一个 ZygoteState 类型的参数。这个类主要封装了一些与 Zygote 进程通信的状态。这个变量是通过 `openZygoteSocketIfNeeded()` 方法得到的，它用来建立与 Zygote 进程之间的 Socket 连接。

```java
    private static Process.ProcessStartResult zygoteSendArgsAndGetResult(
            ZygoteState zygoteState, ArrayList<String> args)
            throws ZygoteStartFailedEx {
        try {
            // ...

            // 获取 Zygote 的读写流
            final BufferedWriter writer = zygoteState.writer;
            final DataInputStream inputStream = zygoteState.inputStream;
    
            // 通过流向 Zygote 写命令
            writer.write(Integer.toString(args.size()));
            writer.newLine();

            for (int i = 0; i < sz; i++) {
                String arg = args.get(i);
                writer.write(arg);
                writer.newLine();
            }

            writer.flush();

            // 读取返回结果
            Process.ProcessStartResult result = new Process.ProcessStartResult();
            result.pid = inputStream.readInt();
            result.usingWrapper = inputStream.readBoolean();

            if (result.pid < 0) {
                throw new ZygoteStartFailedEx("fork() failed");
            }
            return result;
        } catch (IOException ex) {
            zygoteState.close();
            throw new ZygoteStartFailedEx(ex);
        }
    }
```

上面我们也看到了，这里会先从 ZygoteState 中获取输入流和输出流，然后使用流来进行读写。实际上呢，在获取流之前先使用 ZygoteState 的 `connect()` 方法与 Zygote 建立了 Socket 连接。

这里是发送 Socket 给 Zygote，那么远程的 Zygote 是如何对连接进行处理的呢？如果你阅读过我们上一篇文章就会知道，ZygoteServer 启动的时候会执行 `runSelectLoop()` 方法不断对 Socket 进行监听，当收到 AMS 的创建应用进程的请求之后，会调用 Zygote 类的静态方法 `forkAndSpecialize()` 来创建子进程。读者可以参考下面的文章来了解，

[《系统源码-1：Android 系统启动流程源码分析》](https://blog.csdn.net/github_35186068/article/details/86563397)

创建子进程完毕之后会将创建的结果返回给调用者，然后 Zygote 需要对 fork 的子进程的结果进行后续处理，比如启动进程中的方法等。这些将交给 `handleChildProc()` 方法来完成，

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

这里的 `zygoteInit()` 用来对 Zygote 的子进程进行处理。

```java
    public static final Runnable zygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) {
        RuntimeInit.redirectLogStreams();
        RuntimeInit.commonInit();
        ZygoteInit.nativeZygoteInit(); // 启动 Binder 线程池
        return RuntimeInit.applicationInit(targetSdkVersion, argv, classLoader); // 调用到 ActivityThread 的 main 方法
    }
```

它主要做了两件事情：1).启动 Binder 线程池；2).调用到 ActivityThread 的 main 方法，这样程序就进入到了我们的 ActivityThread 中。启动 Binder 线程池的时候，将会通过 JNI 调用进入 AndroidRuntime.cpp 中，

```c++
static void com_android_internal_os_ZygoteInit_nativeZygoteInit(JNIEnv* env, jobject clazz)
{
    gCurRuntime->onZygoteInit();
}
```

这里的 gCurRuntime 就是 AppRuntime 了，它定义在 app_main.cpp 文件中。它会调用下面的方法来启动 Binder 线程池，


```c++
    virtual void onZygoteInit()
    {
        sp<ProcessState> proc = ProcessState::self();
        proc->startThreadPool();
    }
```

## 2、已存在应用进程的时候的启动过程

上面是创建应用进程的过程，下面我们再来看下当应用进程创建之后，应用将如何启动。当我们从 Launcher 页面启动 Activity 的时候会通过 Activity 的 `startActivity()` 启动 Activity. 最终所有的启动 Activity 的操作都将经过 `startActivityForResult()` 方法处理。它将调用 Instrumentation 的 `execStartActivity()` 方法来执行启动操作。

```java
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        // 当前应用的 Binder
        IApplicationThread whoThread = (IApplicationThread) contextThread;
        Uri referrer = target != null ? target.onProvideReferrer() : null;
        if (referrer != null) {
            intent.putExtra(Intent.EXTRA_REFERRER, referrer);
        }
        if (mActivityMonitors != null) {
            // ...
        }
        try {
            intent.migrateExtraStreamToClipData();
            intent.prepareToLeaveProcess(who);
            // 获取 AMS 启动 Activity
            int result = ActivityManager.getService()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, options);
            checkStartActivityResult(result, intent);
        } catch (RemoteException e) {
            throw new RuntimeException("Failure from system", e);
        }
        return null;
    }
```

在这个方法中有两个比较关键的地方，一个是 IApplicationThread，它被用来交给 AMS 继续执行启动操作。AMS 通过 `ActivityManager.getService()`，它用来获取远程的 AMS 的 Binder 来调用。这里的 IApplicationThread 则是当前应用进程的代表，也是一个 Binder. 这样我们可以在当前应用进程中通过 AMS 执行启动操作（实际是在另一个进程完成的）。当启动操作完成了之后，AMS 可以通过当前进程的代表 IApplicationThread 调用本进程的方法来完成启动的后续任务，比如回调各个生命周期方法。

我们先来从整体的角度看一下。如下面的图所示，IApplicationThread 和 AMS 作为两个代表在两个进程之间进行通信。

![Activity 的启动流程](https://user-gold-cdn.xitu.io/2019/1/23/1687abe036d33a83?imageView2/0/w/1280/h/960/format/webp/ignore-error/1)

在上面的一节中，我们已经说明了在应用进程没有创建的时候是如何创建应用进程的。在创建应用进程的时候，会调用 `ActivityThread` 的 main 方法来，这个方法中会启动主线程的 Looper 来创建主线程的消息循环，这个 Looper 对应的消息处理的 Handler 就是 H. 以下面的程序为例，当 AMS 或者其他服务需要回调当前进程的方法的时候，可以直接调用下面的方法。其中的 `scheduleLowMemory()` 方法通过向 H 发送消息来在主线程中执行任务。这里的 `scheduleTransaction()` 是用来执行 Activity 等生命周期回调的。这里的 ClientTransaction 封装了回调的信息。Activity 的生命周期方法就是通过它来回调的。

```java
    private class ApplicationThread extends IApplicationThread.Stub {
 
        @Override
        public void scheduleLowMemory() {
            sendMessage(H.LOW_MEMORY, null);
        }
 
        // ...

        @Override
        public void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
            ActivityThread.this.scheduleTransaction(transaction);
        }
    }

    class H extends Handler {
        // ...
        public static final int LOW_MEMORY              = 124;
        // ...

        public void handleMessage(Message msg) {
            switch (msg.what) {
                // ...
                case LOW_MEMORY:
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "lowMemory");
                    handleLowMemory();
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    break;
                // ...
            }
        }
    }
```

OK，上面我们分析了 Activity 启动过程中的主要的交互逻辑。下面我们就看下 AMS 在启动的过程中做了上面操作。

当启动过程进入到 AMS 之后，它会进行如下的处理，

```java
    public final int startActivityAsUser(/*各种参数*/) {
        enforceNotIsolatedCaller("startActivity");

        userId = mActivityStartController.checkTargetUser(userId, validateIncomingUser,
                Binder.getCallingPid(), Binder.getCallingUid(), "startActivityAsUser");

        return mActivityStartController.obtainStarter(intent, "startActivityAsUser")
                .setCaller(caller)
                // ... 基于构建者模式进行各种赋值
                .execute();

    }
```

它会先通过 `UserHandle.getCallingUserId()` 获取启动的进程的用户信息，然后用 `Binder.getCallingPid()` 和 `Binder.getCallingUid()` 分别获取调用方的进程 ID 和用户 ID. 然后使用 mActivityStartController 检查用户信息是否合法。它内部实际上使用的是 UserController 来进行检查的。当发现用户信息不合法的时候将会抛出一个异常。

然后，它通过 ActivityStartController 的 `obtainStarter()` 方法获取一个 ActivityStarter，使用构建者模式将启动信息传入之后，调用 `execute()` 方法执行启动逻辑。然后程序进入 ActivityStarter 的 `startActivityMayWait()` 方法。该方法中会先对传入的 Intent 的信息进行分析，比如传入的 ACTION_VIEW 等，然后调用 `startActivity()` 方法继续执行，从该方法中返回结果之后再对结果进行处理。随后，程序进入 `startActivityUnchecked()` 方法，这个方法主要负责与 Activity 栈相关的逻辑。Activity 的栈在 AMS 中使用 ActivityStack 类来表示，Activity 实例的信息则使用 ActivityRecord 来表示。

```java
    private int startActivityUnchecked(/*各种参数*/) {
        // ...
        int result = START_SUCCESS;
        if (mStartActivity.resultTo == null && mInTask == null && !mAddingToTask
                && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
            newTask = true;
            // 在新的任务栈中执行任务
            result = setTaskFromReuseOrCreateNewTask(taskToAffiliate, topStack);
        } else if (mSourceRecord != null) {
            result = setTaskFromSourceRecord();
        } else if (mInTask != null) {
            result = setTaskFromInTask();
        } else {
            setTaskToCurrentTopOrCreateNewTask();
        }

        // ...
        if (mDoResume) {
            final ActivityRecord topTaskActivity =
                    mStartActivity.getTask().topRunningActivityLocked();
            if (!mTargetStack.isFocusable()
                    || (topTaskActivity != null && topTaskActivity.mTaskOverlay
                    && mStartActivity != topTaskActivity)) {
                // ...
            } else {
                if (mTargetStack.isFocusable() && !mSupervisor.isFocusedStack(mTargetStack)) {
                    mTargetStack.moveToFront("startActivityUnchecked");
                }
                // 将新的任务栈移动到前台（聚焦）
                mSupervisor.resumeFocusedStackTopActivityLocked(mTargetStack, mStartActivity, mOptions);
            }
        } else if (mStartActivity != null) {
            mSupervisor.mRecentTasks.add(mStartActivity.getTask());
        }
        // ...
        return START_SUCCESS;
    }
```

 `startActivityUnchecked()` 方法会根据 Activity 启动时指定的栈信息来决定创建新的栈还是在启动它的 Activity 所在的栈中执行。因为我们默认的启动类型时 NEW_TASK，因此我们将进入到 `setTaskFromReuseOrCreateNewTask()`。然后，新创建的栈将会被 focus，也就相当于移动到前台。这里调用了 ActivityStackSupervisor 的 `resumeFocusedStackTopActivityLocked()` 方法实现。在该方法中将根据当前的 ActivityRecord 是否已经进入了 RESUMED 状态来进行后续处理，它将调用当前栈的 `resumeTopActivityUncheckedLocked()` 方法。该方法的主要逻辑是对栈的 Activity 进行处理，因为一个新的 Activity 要加入，那么之前的 Activity 需要调用生命周期的方法，比如 `onStop()` 等，还要通知 WMS 进行处理。然后程序进入到 ActivityStackSupervisor 的 `startSpecificActivityLocked()` 方法中执行启动 Activity 真实的罗辑。在新版本的 Android 源码中，它采用如下的方式进行 Activity 的生命周期的回调，

 ```java
     final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app,
            boolean andResume, boolean checkConfig) throws RemoteException {
            // ...
                final ClientTransaction clientTransaction = ClientTransaction.obtain(app.thread, r.appToken);
                clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent),
                        System.identityHashCode(r), r.info,
                        mergedConfiguration.getGlobalConfiguration(),
                        mergedConfiguration.getOverrideConfiguration(), r.compat,
                        r.launchedFromPackage, task.voiceInteractor, app.repProcState, r.icicle,
                        r.persistentState, results, newIntents, mService.isNextTransitionForward(),
                        profilerInfo));
                // ...
                mService.getLifecycleManager().scheduleTransaction(clientTransaction);
        // ...
    }
 ```

最后当调用了 ClientTransaction 的 `schedule()` 方法的时候，它通过 IApplicationThread 的 `scheduleTransaction()` 方法将自身传递给当前应用的进程。当传递到当前进程之后，按照上面我们说的那样回调 Activity 的生命周期即可。

## 总结

在本文中我们分析了 Android 应用启动的源码。其分成两种情形，一个是应用的进程没有创建的时候，此时要通过 Socket 与服务端的 Socket 建立通信，通过 Zygote 创建当前进程的实例。另一个情形是应用已经启动的过程，此时我们的应用会通过 AMS 调用远程的服务，然后将 IApplicationThread 作为信使传递给 AMS，AMS 通过 IApplicationThread 调用当前应用的方法来回调 Activity 等的生命周期。

以上就是 Android 应用启动过程的源码分析，如有疑问，欢迎评论区交流！
