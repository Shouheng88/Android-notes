# AsyncTask 的使用和源码分析

## 1、AsyncTask的使用

使用 `AsyncTask` 可以更加简单地实现任务的异步执行，以及任务执行完毕之后与主线程的交互。它被设计用来执行耗时比较短的任务，通常是几秒种的那种，如果要执行耗时比较长的任务，那么就应该使用 **JUC** 包中的框架，比如 `ThreadPoolExecutor` 和 `FutureTask`等。

AsyncTask 用来在后台线程中执行任务，当任务执行完毕之后将结果发送到主线程当中。它有三个重要的泛类型参数，分别是 `Params`、`Progress` 和 `Result`，分别用来指定参数、进度和结果的值的类型。
以及四个重要的方法，分别是 `onPreExecute()`, `doInBackground()`, `onProgressUpdate()` 和 `onPostExecute()`。
这四个方法中，除了 `doInBackground()`，其他三个都是运行在UI线程的，分别用来处理在任务开始之前、任务进度改变的时候以及任务执行完毕之后的逻辑，而 `doInBackground()` 运行在后台线程中，用来执行耗时的任务。

一种典型的使用方法如下：

```java
private class DownloadFilesTask extends AsyncTask<URL, Integer, Long> {
    
    @Override
    protected Long doInBackground(URL... urls) {
        int count = urls.length;
        long totalSize = 0;
        for (int i = 0; i < count; i++) {
            totalSize += Downloader.downloadFile(urls[i]);
            publishProgress((int) ((i / (float) count) * 100));
            if (isCancelled()) break;
        }
        return totalSize;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        setProgressPercent(progress[0]);
    }

    @Override
    protected void onPostExecute(Long result) {
        showDialog("Downloaded " + result + " bytes");
    }
}
```

上面说 `AsyncTask` 有4个重要的方法，这里我们覆写了3个。`doInBackground()` 运行在线程当中，耗时的任务可以放在这里进行；`onProgressUpdate()` 用来处理当任务的进度发生变化时候的逻辑；`onPostExecute()` 用来处理当任务执行完毕之后的逻辑。另外，这里我们还用到了 `publishProgress()` 和 `isCancelled()` 两个方法，分别用来发布任务进度和判断任务是否被取消。

然后，我们可以用下面的方式来使用它：

```java
    new DownloadFilesTask().execute(url1, url2, url3);
```

使用AsyncTask的时候要注意以下几点内容：

1. AsyncTask 的类必须在主线程中进行加载，当在4.1之后这个过程会自动进行；
2. AsyncTask 的对象必须在主线程中创建；
3. `execute()` 方法必须在UI线程中被调用；
4. 不要直接调用 `onPreExecute()`, `doInBackground()`, `onProgressUpdate()` 和 `onPostExecute()`；
5. 一个AsyncTask对象的 `execute()` 方法只能被调用一次；

Android 1.6 之前，AsyncTask 是**串行**执行任务的；1.6 采用线程池处理**并行**任务；从 3.0 开始，又采用一个线程来**串行**执行任务。
3.0 之后可以用 `executeOnExecutor()` 来并行地执行任务，如果我们希望在3.0之后能并行地执行上面的任务，那么我们应该这样去写：

```java
    new DownloadFilesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url1, url2, url3);
```

这里的 `AsyncTask.THREAD_POOL_EXECUTOR` 是 AsyncTask 内部定义的一个线程池，我们可以使用它来将 AsyncTask 设置成并行的。

## 2、AsyncTask源码分析

### 2.1 AsyncTask 的初始化过程

当初始化一个 AsyncTask 的时候，所有的重载构造方法都会调用下面的这个构造方法。这里做了几件事情：

1. 初始化一个 Handler 对象 mHandler，该 Handler 用来将消息发送到它所在的线程中，通常使用默认的值，即主线程的 Handler；
2. 初始化一个 WorkerRunnable 对象 mWorker。它是一个 `WorkerRunnable` 类型的实例，而 `WorkerRunnable` 又继承自 `Callable`，因此它是一个可以被执行的对象。我们会把在该对象中回调 `doInBackground()` 来将我们的业务逻辑放在线程池中执行。
3. 初始化一个 FutureTask 对象 mFuture。该对象包装了 `mWorker` 并且当 `mWorker` 执行完毕之后会调用它的 `postResultIfNotInvoked()` 方法来通知主线程（不论任务已经执行完毕还是被取消了，都会调用这个方法）。

```java
    public AsyncTask(@Nullable Looper callbackLooper) {
        // 1. 初始化用来发送消息的 Handler
        mHandler = callbackLooper == null || callbackLooper == Looper.getMainLooper()
            ? getMainHandler()
            : new Handler(callbackLooper);

        // 2. 封装一个对象用来执行我们的任务
        mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);
                Result result = null;
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    // 回调我们的业务逻辑
                    result = doInBackground(mParams);
                    Binder.flushPendingCommands();
                } catch (Throwable tr) {
                    mCancelled.set(true);
                    throw tr;
                } finally {
                    // 发送结果给主线程
                    postResult(result);
                }
                return result;
            }
        };

        // 3. 初始化一个 FutureTask，并且当它执行完毕的时候，会调用 postResultIfNotInvoked 来将消息的执行结果发送到主线程中
        mFuture = new FutureTask<Result>(mWorker) {
            @Override
            protected void done() {
                try {
                    // 如果任务没有被触发，也要发送一个结果
                    postResultIfNotInvoked(get());
                } catch (InterruptedException e) {
                    android.util.Log.w(LOG_TAG, e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("An error occurred while executing doInBackground()", e.getCause());
                } catch (CancellationException e) {
                    postResultIfNotInvoked(null);
                }
            }
        };
    }
```

当这样设置完毕之后，我们就可以使用 `execute()` 方法来开始执行任务了。

### 2.2 AsyncTask 中任务的串行执行过程

我们从 `execute()` 方法开始分析 AsyncTask，

```java
    @MainThread
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        return executeOnExecutor(sDefaultExecutor, params);
    }

    @MainThread
    public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec, Params... params) {
        if (mStatus != Status.PENDING) { // 1.判断线程当前的状态
            switch (mStatus) {
                case RUNNING: throw new IllegalStateException(...);
                case FINISHED: throw new IllegalStateException(...);
            }
        }
        mStatus = Status.RUNNING;
        onPreExecute();             // 2.回调生命周期方法
        mWorker.mParams = params;   // 3.赋值给可执行的对象 WorkerRunnable
        exec.execute(mFuture);      // 4.在线程池中执行任务
        return this;
    }
```

当我们调用 AsyncTask 的 `execute()` 方法的时候会立即调用它的 `executeOnExecutor()` 方法。这里传入了两个参数，分别是一个 `Executor` 和任务的参数 `params`。从上面我们可以看出，当直接调用 execute() 方法的时候会使用默认的线程池 `sDefaultExecutor`，而当我们指定了线程池之后，会使用我们指定的线程池来执行任务。

在 1 处，会对 AsyncTask 当前的状态进行判断，这就对应了前面说的，一个任务只能被执行一次。在 2 处会调用 `onPreExecute()` 方法，如果我们覆写了该方法，那么它就会在这个时候被调用。在 3 处的操作是在为 `mWorker` 赋值，即把调用 `execute` 方法时传入的参数赋值给了 `mWorker`。接下来，会将 `mFuture` 添加到线程池中执行。

当我们不指定任何线程池的时候使用的 `sDefaultExecutor` 是一个串行的线程池，它的定义如下：

```java
    public static final Executor SERIAL_EXECUTOR = new SerialExecutor();
    private static volatile Executor sDefaultExecutor = SERIAL_EXECUTOR;

    private static class SerialExecutor implements Executor {
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
        Runnable mActive;

        public synchronized void execute(final Runnable r) {
            mTasks.offer(new Runnable() {
                public void run() {
                    try {
                        // 相当于对传入的Runnable进行了一层包装
                        r.run();
                    } finally {
                        // 分配下一个任务
                        scheduleNext();
                    }
                }
            });
            // 如果当前没有正在执行的任务，那么就尝试从队列中取出并执行
            if (mActive == null) {
                scheduleNext();
            }
        }

        protected synchronized void scheduleNext() {
            // 从队列中取任务并使用THREAD_POOL_EXECUTOR执行
            if ((mActive = mTasks.poll()) != null) {
                THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }
```

从上面我们可以看出，我们添加到线程池中的任务实际上并没有直接交给线程池来执行，而是对其进行了处理之后才执行的，SerialExecutor 通过内部维护了双端队列，每当一个 AsyncTask 调用 `execute()` 方法的时候都会被放在该队列当中进行排队。如果当前没有正在执行的任务，那么就从队列中取一个任务交给 `THREAD_POOL_EXECUTOR` 执行；当一个任务执行完毕之后又会调用 `scheduleNext()` 取下一个任务执行。也就是说，实际上 `sDefaultExecutor` 在这里只是起了一个任务调度的作用，任务最终还是交给 `THREAD_POOL_EXECUTOR` 执行的。

这里的`THREAD_POOL_EXECUTOR`也是一个线程池，它在静态代码块中被初始化：

```java
    static {
        // 使用指定的参数创建一个线程池
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }
```

我们也可以直接将这个静态的线程池作为我们任务执行的线程池而不是放在上面的队列中被串行地执行。

### 2.3 将任务执行的结果发送到其他线程

上面的 `WorkerRunnable` 中已经用到了 `postResult` 方法，它用来将任务执行的结果发送给 `Handler`：

```java
    private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = mHandler.obtainMessage(MESSAGE_POST_RESULT, new AsyncTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }
```

`mHandler` 会在创建 AsyncTask 的时候初始化。我们可以通过 AsyncTask 的构造方法传入 Handler 和 Looper 来指定该对象所在的线程。当我们没有指定的时候，会使用 AsyncTask 内部的 `InternalHandler` 创建 `Handler`：

```java
    private final Handler mHandler;

    public AsyncTask(@Nullable Looper callbackLooper) {
        // 根据传入的参数创建Handler对象
        mHandler = callbackLooper == null || callbackLooper == Looper.getMainLooper() 
            ? getMainHandler() : new Handler(callbackLooper);
    }

    private static Handler getMainHandler() {
        synchronized (AsyncTask.class) {
            if (sHandler == null) {
                // 使用 InternalHandler 创建对象
                sHandler = new InternalHandler(Looper.getMainLooper());
            }
            return sHandler;
        }
    }

    // AsyncTask 内部定义 的Handler 类型
    private static class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override public void handleMessage(Message msg) {
            AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
            // 根据传入的消息类型进行处理
            switch (msg.what) {
                case MESSAGE_POST_RESULT: result.mTask.finish(result.mData[0]); break;
                case MESSAGE_POST_PROGRESS: result.mTask.onProgressUpdate(result.mData); break;
            }
        }
    }
```

## 3、总结

上面我们梳理了 AsyncTask 的大致过程，我们来梳理下：

每当我们实例化一个 AsyncTask 的时候都会在内部封装成一个 Runnable 对象，该对象可以直接放在线程池中执行。这里存在两个线程池，一个是 SerialExecutor 一个是 THREAD_POOL_EXECUTOR，前者主要用来进行任务调度，即把交给线程的任务放在队列中进行排队执行，而时机上所有的任务都是在后者中执行完成的。这个两个线程池都是静态的字段，所以它们对应于整个类的。也就是说，当使用默认的线程池的时候，实例化的 AsyncTask 会一个个地，按照加入到队列中的顺序依次执行。

当任务执行完毕之后，使用 Handler 来将消息发送到主线程即可，这部分的逻辑主要与 Handler 机制相关，可以通过这篇文章来了解：[《Android 消息机制：Handler、MessageQueue 和 Looper》](https://juejin.im/post/5bdec872e51d4551ee2761cb)。

以上就是 AsyncTask 的主要内容。


------
**如果您喜欢我的文章，可以在以下平台关注我：**

- 个人主页：[https://shouheng88.github.io/](https://shouheng88.github.io/)
- 掘金：[https://juejin.im/user/585555e11b69e6006c907a2a](https://juejin.im/user/585555e11b69e6006c907a2a)
- Github：[https://github.com/Shouheng88](https://github.com/Shouheng88)
- CSDN：[https://blog.csdn.net/github_35186068](https://blog.csdn.net/github_35186068)
- 微博：[https://weibo.com/u/5401152113](https://weibo.com/u/5401152113)


