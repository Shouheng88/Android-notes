# RxJava2 系列 （3）：使用 Subject

在这篇文章中，我们会先分析一下 RxJava2 中的 Subject ；然后，我们会使用 Subject 制作一个类似于 EventBus 的全局的通信工具。

在了解本篇文章的内容之前，你需要先了解 RxJava2 中的一些基本的用法，比如 Observable 以及背压的概念，你可以参考我的其他两篇文章来获取这部分内容：[《RxJava2 系列 （1）：一篇的比较全面的 RxJava2 方法总结》](https://juejin.im/post/5b72f76551882561354462dd)和[《RxJava2 系列 （2）：背压和Flowable》](https://juejin.im/post/5b759b9cf265da283719d187)。

## 1、Subject

### 1.1 Subject 的两个特性

Subject 可以同时代表 Observer 和 Observable，允许从数据源中多次发送结果给多个观察者。除了 onSubscribe(), onNext(), onError() 和 onComplete() 之外，所有的方法都是线程安全的。此外，你还可以使用 toSerialized() 方法，也就是转换成串行的，将这些方法设置成线程安全的。

如果你已经了解了 Observable 和 Observer ，那么也许直接看 Subject 的源码定义会更容易理解：

```
public abstract class Subject<T> extends Observable<T> implements Observer<T> {

    // ...
}
```

从上面看出，Subject 同时继承了 Observable 和 Observer 两个接口，说明它既是被观察的对象，同时又是观察对象，也就是可以生产、可以消费、也可以自己生产自己消费。所以，我们可以项下面这样来使用它。这里我们用到的是该接口的一个实现 PublishSubject ：

    public static void main(String...args) {
        PublishSubject<Integer> subject = PublishSubject.create();
        subject.subscribe(System.out::println);

        Executor executor = Executors.newFixedThreadPool(5);
        Disposable disposable = Observable.range(1, 5).subscribe(i ->
                executor.execute(() -> {
                    try {
                        Thread.sleep(i * 200);
                        subject.onNext(i);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
    }

根据程序的执行结果，程序在第200, 400, 600, 800, 1000毫秒依次输出了1到5的数字。

在这里，我们用 PublishSubject 创建了一个**主题**并对其监听，然后在线程当中又通知该主题内容变化，整个过程我们都只操作了 PublishSubject 一个对象。显然，使用 Subject 我们可以达到对一个指定类型的值的结果进行监听的目的——我们把值改变之后对应的逻辑写在 subscribe() 方法中，然后每次调用 onNext() 等方法通知结果之后就可以自动调用 subscribe() 方法进行更新操作。

同时，因为 Subject 实现了 Observer 接口，并且在 Observable 等的 subscribe() 方法中存在一个以 Observer 作为参数的方法（如下），所以，Subject 也是可以作为消费者来对事件进行消费的。

    public final void subscribe(Observer<? super T> observer) 

以上就是 Subject 的两个主要的特性。

### 1.2 Subject 的实现类

在 RxJava2 ，Subject 有几个默认的实现，下面我们对它们之间的区别做简单的说明：

1. `AsyncSubject`:只有当 Subject 调用 onComplete 方法时，才会将 Subject 中的**最后一个事件**传递给所有的 Observer。
2. `BehaviorSubject`:该类有创建时需要一个默认参数，该默认参数会在 Subject 未发送过其他的事件时，向注册的 Observer 发送；新注册的 Observer 不会收到之前发送的事件，这点和 PublishSubject 一致。
3. `PublishSubject`:不会改变事件的发送顺序；在已经发送了一部分事件之后注册的 Observer 不会收到之前发送的事件。
4. `ReplaySubject`:无论什么时候注册 Observer 都可以接收到任何时候通过该 Observable 发射的事件。
5. `UnicastSubject`:只允许一个 Observer 进行监听，在该 Observer 注册之前会将发射的所有的事件放进一个队列中，并在 Observer 注册的时候一起通知给它。

对比 PublishSubject 和 ReplaySubject，它们的区别在于新注册的 Observer 是否能够收到在它注册之前发送的事件。这个类似于 EventBus 中的 StickyEvent 即黏性事件，为了说明这一点，我们准备了下面两段代码：

    private static void testPublishSubject() throws InterruptedException {
        PublishSubject<Integer> subject = PublishSubject.create();
        subject.subscribe(i -> System.out.print("(1: " + i + ") "));

        Executor executor = Executors.newFixedThreadPool(5);
        Disposable disposable = Observable.range(1, 5).subscribe(i -> executor.execute(() -> {
            try {
                Thread.sleep(i * 200);
                subject.onNext(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        Thread.sleep(500);
        subject.subscribe(i -> System.out.print("(2: " + i + ") "));

        Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> ((ExecutorService) executor).shutdown());
    }

    private static void testReplaySubject() throws InterruptedException {
        ReplaySubject<Integer> subject = ReplaySubject.create();
        subject.subscribe(i -> System.out.print("(1: " + i + ") "));

        Executor executor = Executors.newFixedThreadPool(5);
        Disposable disposable = Observable.range(1, 5).subscribe(i -> executor.execute(() -> {
            try {
                Thread.sleep(i * 200);
                subject.onNext(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        Thread.sleep(500);
        subject.subscribe(i -> System.out.print("(2: " + i + ") "));

        Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> ((ExecutorService) executor).shutdown());
    }

它们的输出结果依次是

    PublishSubject的结果：(1: 1) (1: 2) (1: 3) (2: 3) (1: 4) (2: 4) (1: 5) (2: 5)
    ReplaySubject的结果： (1: 1) (1: 2) (2: 1) (2: 2) (1: 3) (2: 3) (1: 4) (2: 4) (1: 5) (2: 5)

从上面的结果对比中，我们可以看出前者与后者的区别在于新注册的 Observer 并没有收到在它注册之前发送的事件。试验的结果与上面的叙述是一致的。

其他的测试代码这不一并给出了，详细的代码可以参考[Github - Java Advanced](https://github.com/Shouheng88/Java-advanced)。

## 2、用 RxJava 打造 EventBus

### 2.1 打造 EventBus

清楚了 Subject 的概念之后，让我们来做一个实践——用 RxJava 打造 EventBus。

我们先考虑用一个全局的 PublishSubject 来解决这个问题，当然，这意味着我们发送的事件不是黏性事件。不过，没关系，只要这种实现方式搞懂了，用 ReplaySubject 做一个发送黏性事件的 EventBus 也非难事。

考虑一下，如果要实现这个功能我们需要做哪些准备：

1. **我们需要发送事件并能够正确地接收到事件。**要实现这个目的并不难，因为 Subject 本身就具有发送和接收两个能力，作为全局的之后就具有了全局的注册和通知的能力。因此，不论你在什么位置发送了事件，任何订阅的地方都能收到该事件。
2. **首先，我们要在合适的位置对事件进行监听，并在合适的位置取消事件的监听。如果我们没有在适当的时机释放事件，会不会造成内存泄漏呢？这还是有可能的。**所以，我们需要对注册监听的观察者进行记录，并提供注册和取消注册的方法，给它们在指定的生命周期中进行调用。

好了，首先是全局的 Subject 的问题，我们可以实现一个静态的或者单例的 Subject。这里我们选择使用后者，所以，我们需要一个单例的方式来使用 Subject：

public class RxBus {

    private static volatile RxBus rxBus;

    private final Subject<Object> subject = PublishSubject.create().toSerialized();

    public static RxBus getRxBus() {
        if (rxBus == null) {
            synchronized (RxBus.class) {
                if(rxBus == null) {
                    rxBus = new RxBus();
                }
            }
        }
        return rxBus;
    }
}

这里我们应用了 DCL 的单例模式提供一个单例的 RxBus，对应一个唯一的 Subject. 这里我们用到了 Subject 的`toSerialized()`，我们上面已经提到过它的作用，就是用来保证 onNext() 等方法的线程安全性。

另外，因为 Observalbe 本身是不支持背压的，所以，我们还需要将该 Observable 转换成 Flowable 来实现背压的效果：

    public <T> Flowable<T> getObservable(Class<T> type){
        return subject.toFlowable(BackpressureStrategy.BUFFER).ofType(type);
    }

这里我们用到的背压的策略是`BackpressureStrategy.BUFFER`，它会缓存发射结果，直到有消费者订阅了它。而这里的`ofType()`方法的作用是用来过滤发射的事件的类型，只有指定类型的事件会被发布。

然后，我们需要记录订阅者的信息以便在适当的时机取消订阅，这里我们用一个`Map<String, CompositeDisposable>`类型的哈希表来解决。这里的`CompositeDisposable`用来存储 Disposable，从而达到一个订阅者对应多个 Disposable 的目的。`CompositeDisposable`是一个 Disposable 的容器，声称可以达到 O(1) 的增、删的复杂度。这里的做法目的是使用注册观察之后的 Disposable 的 dispose() 方法来取消订阅。所以，我们可以得到下面的这段代码：

    public void addSubscription(Object o, Disposable disposable) {
        String key = o.getClass().getName();
        if (disposableMap.get(key) != null) {
            disposableMap.get(key).add(disposable);
        } else {
            CompositeDisposable disposables = new CompositeDisposable();
            disposables.add(disposable);
            disposableMap.put(key, disposables);
        }
    }

    public void unSubscribe(Object o) {
        String key = o.getClass().getName();
        if (!disposableMap.containsKey(key)) {
            return;
        }
        if (disposableMap.get(key) != null) {
            disposableMap.get(key).dispose();
        }
        disposableMap.remove(key);
    }

最后，对外提供一下 Subject 的订阅和发布方法，整个 EventBus 就制作完成了：

    public void post(Object o){
        subject.onNext(o);
    }

    public <T> Disposable doSubscribe(Class<T> type, Consumer<T> next, Consumer<Throwable> error){
        return getObservable(type)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(next,error);
    }

### 2.2 测试效果

我们只需要在最顶层的 Activity 基类中加入如下的代码。这样，我们就不需要在各个 Activity 中取消注册了。然后，就可以使用这些顶层的方法来进行操作了。

    protected void postEvent(Object object) {
        RxBus.getRxBus().post(object);
    }

    protected <M> void addSubscription(Class<M> eventType, Consumer<M> action) {
        Disposable disposable = RxBus.getRxBus().doSubscribe(eventType, action, LogUtils::d);
        RxBus.getRxBus().addSubscription(this, disposable);
    }

    protected <M> void addSubscription(Class<M> eventType, Consumer<M> action, Consumer<Throwable> error) {
        Disposable disposable = RxBus.getRxBus().doSubscribe(eventType, action, error);
        RxBus.getRxBus().addSubscription(this, disposable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RxBus.getRxBus().unSubscribe(this);
    }

在第一个 Activity 中我们对指定的类型的结果进行监听：

    addSubscription(RxMessage.class, rxMessage -> ToastUtils.makeToast(rxMessage.message));

然后，我们在另一个 Activity 中发布事件：

    postEvent(new RxMessage("Hello world!"));

这样当第二个 Activity 中调用指定的发送事件的方法之后，第一个 Activity 就可以接收到发射的事件了。

## 总结

好了，以上就是 Subject 的使用，如果要用一个词来形容它的话，那么只能是“自给自足”了。就是说，它同时做了 Observable 和 Observer 的工作，既可以发射事件又可以对事件进行消费，可谓身兼数职。它在那种想要对某个值进行监听并处理的情形特别有用。因为它不需要你写多个冗余的类，只要它一个就完成了其他两个类来完成的任务，因而代码更加简洁。
