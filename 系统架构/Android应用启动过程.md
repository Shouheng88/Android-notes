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

上面我们也看到了，这里会先从 ZygoteState 中获取输入流和输出流，然后使用流来进行读写。实际上呢，在获取流之前先使用 ZygoteState 的 `connect()` 方法与 Zygote 建立了 Socket 连接。您可以通过阅读这部分代码自行了解。

所以，以上就是 AMS 启动应用进程的部分。

这里是发送 Socket 给 Zygote，那么远程的 Zygote 是如何对连接进行处理的呢？如果你阅读过我们上一篇文章就会知道，ZygoteServer 启动的时候会执行 `runSelectLoop()` 方法不断对 Socket 进行监听，当收到 AMS 的创建应用进程的请求之后，会调用 Zygote 类的静态方法 `forkAndSpecialize()` 来创建子进程。读者可以参考下面的文章来了解，

[《系统源码-1：Android 系统启动流程源码分析》](https://blog.csdn.net/github_35186068/article/details/86563397)

## 2、



