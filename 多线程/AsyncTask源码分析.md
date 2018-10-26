# AsyncTask的使用和源码分析

## 1、AsyncTask的使用

使用`AsyncTask`可以更加简单地实现任务的异步执行，以及任务执行完毕之后与主线程的交互。它被设计用来执行耗时比较短的任务，通常是几秒种的那种，如果要执行耗时比较长的任务，那么就应该使用**JUC**包中的框架，比如`ThreadPoolExecutor`和`FutureTask`等。

AsyncTask用来在后台线程中执行任务，当任务执行完毕之后将结果发送到主线程当中。它有三个重要的泛类型参数，分别是`Params`, `Progress`, `Result`，分别用来指定参数、进度和结果的值的类型。以及四个重要的方法，分别是`onPreExecute`, `doInBackground`, `onProgressUpdate`和`onPostExecute`。这四个方法中，除了`doInBackground`，其他三个都是运行在UI线程的，分别用来处理在任务开始之前、任务进度改变的时候以及任务执行完毕之后的逻辑，而`doInBackground`运行在后台线程中，用来执行耗时的任务。

一种典型的使用方法如下：

```
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

上面说`AsyncTask`有4个重要的方法，这里我们覆写了3个。`doInBackground`运行在线程当中，耗时的任务可以放在这里进行；`onProgressUpdate`用来处理当任务的进度发生变化时候的逻辑；`onPostExecute`用来处理当任务执行完毕之后的逻辑。另外，这里我们还用到了`publishProgress`和`isCancelled`两个方法，分别用来发布任务进度和判断任务是否被取消。

然后，我们可以用下面的方式来使用它：

    new DownloadFilesTask().execute(url1, url2, url3);

使用AsyncTask的时候要注意以下几点内容：

1. AsyncTask的类必须在主线程中进行加载，当在4.1之后这个过程会自动进行；
2. AsyncTask的对象必须在主线程中创建；
3. `execute`方法必须在UI线程中被调用；
4. 不要直接调用`onPreExecute`, `doInBackground`, `onProgressUpdate`和`onPostExecute`；
5. 一个AsyncTask对象的`execute`方法只能被调用一次；
6. Android 1.6之前，AsyncTask是串行执行任务的；1.6采用线程池处理并行任务；从3.0开始，又采用一个线程来串行执行任务。
7. 3.0之后可以用`executeOnExecutor`来并行地执行任务，如果我们希望在3.0之后能并行地执行上面的任务，那么我们应该这样去写：

        new DownloadFilesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url1, url2, url3);

## 2、AsyncTask源码分析

### 2.1 串行执行

我们从`execute`方法开始分析AsyncTask，

    @MainThread
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        return executeOnExecutor(sDefaultExecutor, params);
    }

    @MainThread
    public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec, Params... params) {
        if (mStatus != Status.PENDING) { // 1
            switch (mStatus) {
                case RUNNING: throw new IllegalStateException(...);
                case FINISHED: throw new IllegalStateException(...);
            }
        }
        mStatus = Status.RUNNING;
        onPreExecute(); // 2
        mWorker.mParams = params; // 3
        exec.execute(mFuture); // 4
        return this;
    }

当我们调用AsyncTask的`execute`方法的时候会立即调用它的`executeOnExecutor`方法，这里传入了两个参数，分别是一个`Executor`和任务的参数`params`。在1处，会对AsyncTask当前的状态进行判断，这就对应了前面说的，一个任务只能被执行一次。在2处会调用`onPreExecute`方法，如果我们覆写了该方法，那么它就会在这个时候被调用。3处的操作是在为`mWorker`赋值，即把调用`execute`方法时传入的参数赋值给了它。`mWorker`是一个`WorkerRunnable`类型的实例，而`WorkerRunnable`又继承自`Callable`。在AsyncTask的构造方法中会创建并初始化它，并将其包装成一个`FutureTask`类型的字段，即`mFuture`。而在4处，我们就将使用传入的`Executor`来执行该`mFuture`。以下代码是`mWorker`和`mFuture`的相关内容：

    mWorker = new WorkerRunnable<Params, Result>() {
        public Result call() throws Exception {
            // 原子的布尔类型，设置为true标记任务为已经开始的状态
            mTaskInvoked.set(true);
            Result result = null;
            try {
                // 我们用来执行后台逻辑的方法会在这里被回调
                result = doInBackground(mParams);
            } catch (Throwable tr) {
                // 也是原子布尔类型的引用，用来标记任务为已经取消的状态
                mCancelled.set(true);
                throw tr;
            } finally {
                // 发送执行的结果
                postResult(result);
            }
            return result;
        }
    };

    // 将上面的mWorker包装成FutureTask对象
    mFuture = new FutureTask<Result>(mWorker) {...};

`execute`方法中使用到的`sDefaultExecutor`是一个串行的线程池，它的定义如下所示：

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

注意以下这里的`sDefaultExecutor`是一个静态变量，默认情况下，`AsyncTask`的所有的实例都会放在该线程池当中被依次、串行地**调度**。它通过内部维护的双端队列，每当一个AsyncTask调用`execute`方法的时候都会被放在该队列当中进行排队。如果当前没有正在执行的任务，那么就从队列中取一个任务交给`THREAD_POOL_EXECUTOR`执行，当一个任务执行完毕之后又会调用`scheduleNext`取下一个任务执行。也就是说，实际上`sDefaultExecutor`在这里只是起了一个任务调度的作用，而任务最终还是交给`THREAD_POOL_EXECUTOR`执行的。这里的`THREAD_POOL_EXECUTOR`也是一个线程池，它在静态代码块中被初始化：

    static {
        // 使用指定的参数创建一个线程池
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

### 2.2 通信

上面的`WorkerRunnable`中已经用到了`postResult`方法，它用来将任务执行的结果发送给`Handler`。

    private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = mHandler.obtainMessage(MESSAGE_POST_RESULT, new AsyncTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }

这里通过`mHandler`发送信息。`mHandler`会在创建AsyncTask的时候被创建，当传入的`Looper`为null的时候，会使用AsyncTask内部的`InternalHandler`创建`Handler`：

    private final Handler mHandler;

    public AsyncTask(@Nullable Looper callbackLooper) {
        // 根据传入的参数创建Handler对象
        mHandler = callbackLooper == null || callbackLooper == Looper.getMainLooper() 
            ? getMainHandler() : new Handler(callbackLooper);
    }

    private static Handler getMainHandler() {
        synchronized (AsyncTask.class) {
            if (sHandler == null) {
                // 使用InternalHandler创建对象
                sHandler = new InternalHandler(Looper.getMainLooper());
            }
            return sHandler;
        }
    }

    // AsyncTask内部定义的Handler类型
    private static class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override public void handleMessage(Message msg) {
            AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
            switch (msg.what) {
                case MESSAGE_POST_RESULT: result.mTask.finish(result.mData[0]); break;
                case MESSAGE_POST_PROGRESS: result.mTask.onProgressUpdate(result.mData); break;
            }
        }
    }

在一些书上可能会看到，说`Handler`是静态类型的，但我在源码中看到的是非静态类型的。静态类型的`Handler`就要求AsyncTask类必须在主线程中进行加载，因为静态成员会在类记载的时候进行初始化。如果我们在非主线程中用`new Handler()`创建了一个Handler，那么它会默认使用当前线程对应的`Looper`创建一个实例。但我们看上面的代码，这里在创建`Handler`的时候使用了`Looper.getMainLooper()`来获取主线程对应的`Looper`，因此AsyncTask类必须在主线程中进行加载就不是必需的了。

AsyncTask中还有一些其他的细节和方法，但通过上面主体内容的分析，理解它们已经不是问题，因此不再继续分析下去，感兴趣的可以看下源码的实现。

