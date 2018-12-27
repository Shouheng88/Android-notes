# ANR

## ANR 出现的情形

1. 输入事件(按键和触摸事件) 5s 内没被处理；
2. BroadcastReceiver 的事件( `onRecieve()` 方法)在规定时间内没处理完(前台广播为 10s，后台广播为 60s)；
3. Service 前台 20s 后台 200s 未完成启动；
4. ContentProvider 的 `publish()` 在 10s 内没进行完。

通常情况下就是主线程被阻塞造成的

## ANR 的实现原理

以广播为例，会在开始执行任务之前发送一条消息，执行完毕之后撤销这条消息，如果执行因为超时的原因没有撤销就弹出 ANR 提示了。

    final void processNextBroadcast(boolean fromMsg) {
        synchronized(mService) {
            ...
            // step 2: 处理当前有序广播
            do {
                r = mOrderedBroadcasts.get(0);
                // 获取所有该广播所有的接收者
                int numReceivers = (r.receivers != null) ? r.receivers.size() : 0;
                if (mService.mProcessesReady && r.dispatchTime > 0) {
                    long now = SystemClock.uptimeMillis();
                    if ((numReceivers > 0) &&
                            (now > r.dispatchTime + (2*mTimeoutPeriod*numReceivers))) {
                        // 当广播处理时间超时，则强制结束这条广播
                        broadcastTimeoutLocked(false);
                        ...
                    }
                }
                if (r.receivers == null || r.nextReceiver >= numReceivers
                        || r.resultAbort || forceReceive) {
                    if (r.resultTo != null) {
                        // 处理广播消息消息
                        performReceiveLocked(r.callerApp, r.resultTo,
                            new Intent(r.intent), r.resultCode,
                            r.resultData, r.resultExtras, false, false, r.userId);
                        r.resultTo = null;
                    }
                    // 取消BROADCAST_TIMEOUT_MSG消息
                    cancelBroadcastTimeoutLocked();
                }
            } while (r == null);
            ...

            // step 3: 获取下条有序广播
            r.receiverTime = SystemClock.uptimeMillis();
            if (!mPendingBroadcastTimeoutMessage) {
                long timeoutTime = r.receiverTime + mTimeoutPeriod;
                // 设置广播超时时间，发送BROADCAST_TIMEOUT_MSG
                setBroadcastTimeoutLocked(timeoutTime);
            }
            ...
        }
    }

参考：[《Android ANR原理分析》](https://www.cnblogs.com/android-blogs/p/5718302.html)

## ANR 的解决办法

### 1. 使用 adb 导出 ANR 日志并进行分析

发生 ANR　的时候系统会记录　ANR　的信息并将其存储到　`/data/anr/traces.txt`　文件中（在比较新的系统中会被存储都　`/data/anr/anr_*`　文件中）。我们可以使用下面的方式来将其导出到电脑中以便对　ANR　产生的原因进行分析：

    adb root
    adb shell ls /data/anr
    adb pull /data/anr/<filename>

*在笔者分析 ANR 的时候使用上述指令尝试导出 ANR 日志的时候都出现了 Permission Denied。此时，你可以将手机 Root 之后导出，或者尝试修改文件的读写权限，或者在开发者模式中选择将日志导出到 sdcard 之后再从 sdcard 将日志发送到电脑端进行查看*

### 2. 使用 DDMS 的 traceview 进行分析

在 AS 中打开 DDMS，或者到 SDK 安装目录的 tools 目录下面使用 `monitor.bat` 打开 DDMS。

TraceView 工具的使用可以参考这篇文章：[《Android 性能分析之TraceView使用(应用耗时分析)》](https://blog.csdn.net/android_jianbo/article/details/76608558)

使用 TraceView 来通过耗时方法调用的信息定位耗时操作的位置。

资料：

- [developer.android.com](https://developer.android.com/topic/performance/vitals/anr)
- [《Android 性能分析之TraceView使用(应用耗时分析)》](https://blog.csdn.net/android_jianbo/article/details/76608558)： TraceView 工具使用
