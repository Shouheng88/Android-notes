# Android 多线程编程：IntentService & HandlerThread

因为 Android 是使用 Java 开发的，所以当我们谈及 Android 中的多线程，必然绕不过 Java 中的多线程编程。但在这篇文章中，我们不会过多地分析 Java 中的多线程编程的知识。我们会在以后分析 Java 并发编程的时候分析 Java 中的多线程、线程池和并发 API 的用法。

我们先来总结一下 Android 多线程编程的演变过程：首先是 Java 的 Thread。因为本身在创建一个线程和销毁一个线程的时候会有一定的开销，当我们任务的执行时间相比于这个开销很小的时候，单独创建一个线程就显得不划算。所以，当程序中存在大量的、小的任务的时候，建议使用线程池来进行管理。但我们一般也很少主动去创建线程池，这是因为——也许是考虑到开发者自己去维护一个线程池比较复杂—— Android 中已经为我们设计了 AsyncTask。AsyncTask 内部封装了一个线程池，我们可以使用它来执行耗时比较短的任务。但 AsyncTask 也有一些缺点：1).如果你的程序中存在很多的不同的任务的时候，你可能要为每个任务定义一个 AsyncTask 的子类。2).从异步线程切换到主线程的方式不如 RxJava 简洁。所以，在实际开发的过程中，我通常使用 RxJava 来实现异步的编程。尤其是局部的优化、不值得专门定义一个 AsyncTask 类的时候，RxJava 用起来更加舒服。

上面的多线程创建的只是普通的线程，对系统来说，优先级比较低。在 Android 中还提供了 IntentService 来执行优先级相对较高的任务。启动一个 IntentService 任务的时候会将任务添加到其内部的、异步的消息队列中执行。此外，IntentService 又继承自 Service，所以这让它具有异步和较高的优先级两个优势。

在之前的文章中，我们已经分析过 AsyncTask、RxJava 以及用来实现线程切换的 Handler. 这里奉上这些文章的链接：

- [《Android AsyncTask 源码分析》](https://juejin.im/post/5b65c71af265da0f9402ca4a)
- [《RxJava2 系列 (1)：一篇的比较全面的 RxJava2 方法总结》](https://juejin.im/post/5b72f76551882561354462dd)
- [《RxJava2 系列 (2)：背压和Flowable》](https://juejin.im/post/5b759b9cf265da283719d187)
- [《RxJava2 系列 (3)：使用 Subject》](https://juejin.im/post/5b801dfa51882542cb409905)
- [《Android 消息机制：Handler、MessageQueue 和 Looper》](https://juejin.im/post/5bdec872e51d4551ee2761cb)

你可以通过以上的文章来了解这部分的内容。在本篇文章中，我们主要来梳理下另外两个多线程相关的 API，HandlerThread 和 IntentService。

## 1、异步消息队列：HandlerThread

如果你之前还没有了解过 Handler 的实现的话，那么最好通过我们上面的那篇文章 [《Android 消息机制：Handler、MessageQueue 和 Looper》](https://juejin.im/post/5bdec872e51d4551ee2761cb) 了解一下。因为 HandlerThread 就是通过封装一个 Looper 来实现的。

### 1.1 HandlerThread 的使用

HandlerThread 继承自线程类 Thread，内部又维护了一个 Looper，Looper 内又维护了一个消息队列。所以，我们可以使用 HandlerThread 来创建一个异步的线程，然后不断向该线程发送任务。这些任务会被封装成消息放进 HandlerThread 的消息队列中被执行。所以，我们可以用 HandlerThread 来创建异步的消息队列。

在使用 HandlerThread 的时候有两个需要注意的地方：

1. 因为 HandlerThread 内部的 Looper 的初始化和开启循环的过程都在 `run()` 方法中执行，所以，在使用 HandlerThread 之前，你必须调用它的 start() 方法。
2. 因为 HandlerThread 的 `run()` 方法使用 Looper 开启一个了无限循环，所以，当不再使用它的时候，应该调用它的 `quitSafely()` 或 `quit()` 方法来结束该循环。

在使用 HandlerThread 的时候只需要创建一个它的实例，然后使用它的 Looper 来创建 Handler 实例，并通过该 Handler 发送消息来将任务添加到队列中。下面是一个使用示例：

    myHandlerThread = new HandlerThread("MyHandlerThread");
    myHandlerThread.start();
    handler = new Handler( myHandlerThread.getLooper() ){
        @Override
        public void handleMessage(Message msg) {
            // ... do something
        }
    };
    handler.sendEmptyMessage(1);

这里我们创建了 HandlerThread 实例之后用它来创建 Handler 然后通过 Handler 把任务加入到消息队列中进行执行。

显然，使用 HandlerThread 可以很轻松地实现一个消息队列。你只需要在创建了 Handler 之后向它发送消息，然后所有的任务将被加入到队列中执行。当然，它也有缺点。因为所有的任务将会被按顺序执行，所以一旦队列中有某个任务执行时间过长，那么就会导致后续的任务都会被延迟处理。

### 1.2 HandlerThread 源码解析

下面是该 API 的源码，实现也比较简单，我们直接通过注释来对主要部分进行说明：

    public class HandlerThread extends Thread {
        int mPriority;
        int mTid = -1;
        Looper mLooper;
        private @Nullable Handler mHandler;

        public HandlerThread(String name) {
            super(name);
            mPriority = Process.THREAD_PRIORITY_DEFAULT;
        }
        
        protected void onLooperPrepared() { }

        // 在这个方法开启了 Looper 循环，因为是一个无限循环，所以不适用的时候应该将其停止
        @Override
        public void run() {
            mTid = Process.myTid();
            Looper.prepare();
            synchronized (this) {
                mLooper = Looper.myLooper();
                notifyAll();
            }
            Process.setThreadPriority(mPriority);
            onLooperPrepared();
            Looper.loop();
            mTid = -1;
        }
        
        // 获取该 HandlerThread 对应的 Looper
        public Looper getLooper() {
            if (!isAlive()) {
                return null;
            }
            synchronized (this) {
                while (isAlive() && mLooper == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) { }
                }
            }
            return mLooper;
        }

        public boolean quit() {
            Looper looper = getLooper();
            if (looper != null) {
                looper.quit();
                return true;
            }
            return false;
        }

        public boolean quitSafely() {
            Looper looper = getLooper();
            if (looper != null) {
                looper.quitSafely();
                return true;
            }
            return false;
        }

        // ... 无关代码
    }

上面的代码比较简单明了，在 run() 方法中初始化 Looper 并执行。如果 Looper 还没有被创建，那么当调用 `getLooper()` 方法获取 Looper 的时候会让线程阻塞。当 Looper 创建完毕之后会唤醒所有阻塞的线程继续执行。另外，就是两个停止 Looper 的方法。它们基本上就是对 Looper 进行了一层封装。

## 2、IntentService

### 2.1 使用 IntentService

IntentService 继承自 Serivce，因此它比普通的多线程任务优先级要高。这使得它相比于普通的异步任务不容易被系统 kill 掉。它内部也是通过一个 Looper 来实现的，所以也是一种消息队列。在研究它的源码之前，我们先来看一下它的使用。

IntentService 的使用是比较简单的，只需要：1).继承它并实现其中的 `onHandleIntent()` 方法；2). 将 IntentService 注册到 manifest 中；3). 像开启一个普通的服务那样开启一个 IntentService 即可。下面是该类的一个使用示例：

    public class FileRecognizeTask extends IntentService {

        public static void start(Context context) {
            Intent intent = new Intent(context, FileRecognizeTask.class);
            context.startService(intent);
        }
        
        public FileRecognizeTask() {
            super("FileRecognizeTask");
        }

        @Override
        protected void onHandleIntent(@androidx.annotation.Nullable @Nullable Intent intent) {
            // 你的需要异步执行的业务逻辑
        }
    }

OK，介绍完了 IntentService 的使用，我们再来分析一下它的源码。

### 2.2 IntentService 源码分析

实现 IntentService 的时候使用到了我们上面分析过的 HandlerThread. 首先，在 `onCreate()` 回调方法中创建了一个 HandlerThread，然后使用它的 Looper 创建了一个 ServiceHandler：

    HandlerThread thread = new HandlerThread("IntentService[" + mName + "]");
    thread.start();
    mServiceLooper = thread.getLooper();
    mServiceHandler = new ServiceHandler(mServiceLooper);

ServiceHandler 是 IntentSerice 的内部类，其定义如下：

    private final class ServiceHandler extends Handler {

        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
            stopSelf(msg.arg1);
        }
    }

ServiceHandler 用来执行被添加到队列中的消息。它会回调 IntentService 中的 `onHandleIntent()` 方法，也就是我们实现业务逻辑的方法。当消息执行完毕之后，会调用 Service 的 `stopSelf(int)` 方法来尝试停止服务。注意这里调用的是 `stopSelf(int)` 而不是 `stopSelf()`。它们之间的区别是，当还存在没有完成的任务的时候 `stopSelf(int)` 不会立即停止服务，而 `stopSelf()` 方法会立即停止服务。

IntentSerivce 的 `onCreate()` 方法会在第一次启动的时候被调用，来创建服务。而 `onStartCommond()` 方法会在每次启动的时候被调用。下面是该方法的定义。

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return mRedelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

`onStartCommand()` 内部调用了 `onStart()` 来处理请求。在 `onStart()` 方法中会通过 mServiceHandler 创建一个消息，并将该消息发送给 mServiceHandler. 该消息会在被 mServiceHandler 放进消息队列中排队，并在合适的时机被执行。

因此，我们可以总结一下 IntentService 的工作过程：首先，当我们第一次启动 IntentService 的时候会初始化一个 Looper 和 Handler；然后调用它的 onStartCommond() 方法，把请求包装成消息之后发送到消息队列中等待执行；当消息被 Handler 处理的时候会回调 IntentService 的 `onHandleIntent()` 方法来执行。此时，如果又有一个任务需要执行，那么 IntentService 的 onStartCommond() 方法会再次被执行并把请求封装之后放入队列中。当队列中的所有的消息都执行完毕，并且没有新加入的请求，那么此时服务就会自动停止，否则服务还会继续在后台执行。

这里，同样也应该注意下，IntentService 中的任务是按照被添加的顺序来执行的。

## 总结

以上就是我们对 IntentService 和 HandlerThread 的分析。它们都是使用了 Handler 来实现，所以搞懂它们的前提是搞懂 Handler。关于 Handler，还是推荐一下笔者的另一篇文章 [《Android 消息机制：Handler、MessageQueue 和 Looper》](https://juejin.im/post/5bdec872e51d4551ee2761cb)。


------
**我是 WngShhng. 如果您喜欢我的文章，可以在以下平台关注我：**

- 个人主页：[https://shouheng88.github.io/](https://shouheng88.github.io/)
- 掘金：[https://juejin.im/user/585555e11b69e6006c907a2a](https://juejin.im/user/585555e11b69e6006c907a2a)
- Github：[https://github.com/Shouheng88](https://github.com/Shouheng88)
- CSDN：[https://blog.csdn.net/github_35186068](https://blog.csdn.net/github_35186068)
- 微博：[https://weibo.com/u/5401152113](https://weibo.com/u/5401152113)


