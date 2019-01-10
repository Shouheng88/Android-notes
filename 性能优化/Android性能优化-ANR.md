# Android 性能优化 - ANR 的原因和解决方案

## 1、出现 ANR 的情况

满足下面的一种情况系统就会弹出 ANR 提示

1. 输入事件(按键和触摸事件) 5s 内没被处理；
2. BroadcastReceiver 的事件 ( `onRecieve()` 方法) 在规定时间内没处理完 (前台广播为 10s，后台广播为 60s)；
3. Service 前台 20s 后台 200s 未完成启动；
4. ContentProvider 的 `publish()` 在 10s 内没进行完。

通常情况下就是主线程被阻塞造成的。

## 2、ANR 的实现原理

以输入无响应的过程为例（基于 9.0 代码）：

最终弹出 ANR 对话框的位置是与 AMS 同目录的类 `AppErrors` 的 `handleShowAnrUi()` 方法。这个类用来处理程序中出现的各种错误，不只 ANR，强行 Crash 也在这个类中处理。

```java
    // base/core/java/com/android/server/am/AppErrors.java
    void handleShowAnrUi(Message msg) {
        Dialog dialogToShow = null;
        synchronized (mService) {
            AppNotRespondingDialog.Data data = (AppNotRespondingDialog.Data) msg.obj;
            // ...

            Intent intent = new Intent("android.intent.action.ANR");
            if (!mService.mProcessesReady) {
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND);
            }
            mService.broadcastIntentLocked(null, null, intent,
                    null, null, 0, null, null, null, AppOpsManager.OP_NONE,
                    null, false, false, MY_PID, Process.SYSTEM_UID, 0 /* TODO: Verify */);

            boolean showBackground = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.ANR_SHOW_BACKGROUND, 0) != 0;
            if (mService.canShowErrorDialogs() || showBackground) {
                dialogToShow = new AppNotRespondingDialog(mService, mContext, data);
                proc.anrDialog = dialogToShow;
            } else {
                MetricsLogger.action(mContext, MetricsProto.MetricsEvent.ACTION_APP_ANR,
                        AppNotRespondingDialog.CANT_SHOW);
                // Just kill the app if there is no dialog to be shown.
                mService.killAppAtUsersRequest(proc, null);
            }
        }
        // If we've created a crash dialog, show it without the lock held
        if (dialogToShow != null) {
            dialogToShow.show();
        }
    }
```

不过从发生 ANR 的地方调用到这里要经过很多的类和方法。最初抛出 ANR 是在 `InputDispatcher.cpp` 中。我们可以通过其中定义的常量来寻找最初触发的位置：

```C++
// native/services/inputflinger/InputDispatcher.cpp
constexpr nsecs_t DEFAULT_INPUT_DISPATCHING_TIMEOUT = 5000 * 1000000LL; // 5 sec
```

从这个类触发的位置会经过层层传递达到 `InputManagerService` 中

```java
    // base/services/core/java/com/android/server/input/InputManagerService.java
    private long notifyANR(InputApplicationHandle inputApplicationHandle,
            InputWindowHandle inputWindowHandle, String reason) {
        return mWindowManagerCallbacks.notifyANR(
                inputApplicationHandle, inputWindowHandle, reason);
    }
```

这里的 `mWindowManagerCallbacks` 就是 `InputMonitor` ：

```java
    // base/services/core/java/com/android/server/wm/InputMonitor.java
    public long notifyANR(InputApplicationHandle inputApplicationHandle,
            InputWindowHandle inputWindowHandle, String reason) {
        // ... 略

        if (appWindowToken != null && appWindowToken.appToken != null) {
            final AppWindowContainerController controller = appWindowToken.getController();
            final boolean abort = controller != null
                    && controller.keyDispatchingTimedOut(reason,
                            (windowState != null) ? windowState.mSession.mPid : -1);
            if (!abort) {
                return appWindowToken.mInputDispatchingTimeoutNanos;
            }
        } else if (windowState != null) {
            try {
                // 使用 AMS 的方法
                long timeout = ActivityManager.getService().inputDispatchingTimedOut(
                        windowState.mSession.mPid, aboveSystem, reason);
                if (timeout >= 0) {
                    return timeout * 1000000L; // nanoseconds
                }
            } catch (RemoteException ex) {
            }
        }
        return 0; // abort dispatching
    }
```

然后回在上述方法调用 AMS 的 `inputDispatchingTimedOut()` 方法继续处理，并最终在 `inputDispatchingTimedOut()` 方法中将事件传递给 `AppErrors`

```java
    // base/services/core/java/com/android/server/am/ActivityManagerService.java
    public boolean inputDispatchingTimedOut(final ProcessRecord proc,
            final ActivityRecord activity, final ActivityRecord parent,
            final boolean aboveSystem, String reason) {
        // ...

        if (proc != null) {
            synchronized (this) {
                if (proc.debugging) {
                    return false;
                }

                if (proc.instr != null) {
                    Bundle info = new Bundle();
                    info.putString("shortMsg", "keyDispatchingTimedOut");
                    info.putString("longMsg", annotation);
                    finishInstrumentationLocked(proc, Activity.RESULT_CANCELED, info);
                    return true;
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 使用 AppErrors 继续处理
                    mAppErrors.appNotResponding(proc, activity, parent, aboveSystem, annotation);
                }
            });
        }

        return true;
    }
```

当事件传递到了 `AppErrors` 之后，它会借助 Handler 处理消息也就调用了最初的那个方法并弹出对话框。

参考：[《Android ANR原理分析》](https://www.cnblogs.com/android-blogs/p/5718302.html)

## 3、ANR 的解决办法

上面分析了 ANR 的成因和原理，下面我们分析下如何解决 ANR. 

### 1. 使用 adb 导出 ANR 日志并进行分析

发生 ANR的时候系统会记录 ANR 的信息并将其存储到 `/data/anr/traces.txt` 文件中（在比较新的系统中会被存储都 `/data/anr/anr_*` 文件中）。我们可以使用下面的方式来将其导出到电脑中以便对 ANR 产生的原因进行分析：

    adb root
    adb shell ls /data/anr
    adb pull /data/anr/<filename>

*在笔者分析 ANR 的时候使用上述指令尝试导出 ANR 日志的时候都出现了 Permission Denied。此时，你可以将手机 Root 之后导出，或者尝试修改文件的读写权限，或者在开发者模式中选择将日志导出到 sdcard 之后再从 sdcard 将日志发送到电脑端进行查看*

### 2. 使用 DDMS 的 traceview 进行分析

在 AS 中打开 DDMS，或者到 SDK 安装目录的 tools 目录下面使用 `monitor.bat` 打开 DDMS。

TraceView 工具的使用可以参考这篇文章：[《Android 性能分析之TraceView使用(应用耗时分析)》](https://blog.csdn.net/android_jianbo/article/details/76608558)

这种定位 ANR 的思路是：**使用 TraceView 来通过耗时方法调用的信息定位耗时操作的位置**。

资料：

- [《ANR 官方文档》](https://developer.android.com/topic/performance/vitals/anr)
- [《Android 性能分析之TraceView使用(应用耗时分析)》](https://blog.csdn.net/android_jianbo/article/details/76608558)

### 3. 常见的 ANR 场景

1. I/O 阻塞
2. 网络阻塞
3. 多线程死锁
4. 由于响应式编程等导致的方法死循环
5. 由于某个业务逻辑执行的时间太长

### 4. 避免 ANR 的方法

1. UI 线程尽量只做跟 UI 相关的工作；
2. 耗时的工作 (比如数据库操作，I/O，网络操作等)，采用单独的工作线程处理；
3. 用 Handler 来处理 UI 线程和工作线程的交互；
4. 使用 RxJava 等来处理异步消息。

总之，一个原则就是：**不在主线程做耗时操作**。

