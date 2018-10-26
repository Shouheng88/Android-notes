# Handler相关

## 1、内存泄漏问题

按照如下的方式使用Handler，会在程序中给出一个提示：`In Android, Handler classes should be static or leaks might occur.`

    public class SampleActivity extends Activity {

        private final Handler mLeakyHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                // ... 
            }
        }
    }

启动Android应用程序时，framework会为该应用程序的主线程创建一个Looper对象。这个Looper对象包含一个简单的消息队列Message Queue，并且能够循环的处理队列中的消息。这些消息包括大多数应用程序framework事件，例如Activity生命周期方法调用、button点击等，这些消息都会被添加到消息队列中并被逐个处理。另外，主线程的Looper对象会伴随该应用程序的整个生命周期。

然后，当主线程里，实例化一个Handler对象后，它就会自动与主线程Looper的消息队列关联起来。所有发送到消息队列的消息Message都会拥有一个对Handler的引用，所以当Looper来处理消息时，会据此回调Handler.handleMessage(Message)方法来处理消息。

可能引起内存泄漏的代码：

    public class SampleActivity extends Activity {

        private final Handler mLeakyHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                /* .. */
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLeakyHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                   /* ... */ 
                }
            }, 1000 * 60 * 10);
            finish();
        }
    }

两个内存泄漏点：

1. 当activity结束(finish)时，里面的延时消息在得到处理前，会一直保存在主线程的消息队列里持续10分钟。而且，由上文可知，这条消息持有对handler的引用，而handler又持有对其外部类（在这里，即SampleActivity）的潜在引用。这条引用关系会一直保持直到消息得到处理，从而，这阻止了SampleActivity被垃圾回收器回收，同时造成应用程序的泄漏。
2. Runnable类是非静态匿名类，同样持有对其外部类的引用，从而也导致泄漏。

我们看下面的解决办法：

    public class SampleActivity extends Activity {

        private static class MyHandler extends Handler {

            private final WeakReference<SampleActivity> mActivity;

            public MyHandler(SampleActivity activity) {
                mActivity = new WeakReference<SampleActivity>(activity);
            }

            @Override
            public void handleMessage(Message msg) {
                SampleActivity activity = mActivity.get();
                if (activity != null) {
                    // ...
                }
            }
        }

        private final MyHandler mHandler = new MyHandler(this);

        private static final Runnable sRunnable = new Runnable() {
            @Override
            public void run() { 
                // ...
            }
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mHandler.postDelayed(sRunnable, 1000 * 60 * 10);

            finish();
        }
    }

可以看出，在这里首先将Handler和Runnable声明成静态的，这样的好处是静态内部类不会持有外部类的引用，所以就不会造成内存泄漏。另外，我们注意到在MyHandler中还持有一个对外部类SampleActivity的弱引用。这样该SampleActivity就是可以回收的了。

总结：

如果一个内部类实例的生命周期比Activity更长，那么我们千万不要使用非静态的内部类。最好的做法是，使用静态内部类，然后在该类里使用弱引用来指向所在的Activity。

参考：

1. [http://www.jianshu.com/p/cb9b4b71a820](http://www.jianshu.com/p/cb9b4b71a820)