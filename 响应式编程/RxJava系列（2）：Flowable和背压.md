# RxJava2 系列 （2）：背压和Flowable

背压(Back Pressure)的概念最初并不是在响应式编程中提出的，它最初用在流体力学中，指的是后端的压力，
通常用于描述系统排出的流体在出口处或二次侧受到的与流动方向相反的压力。

在响应式编程中，我们可以将产生信息的部分叫做上游或者叫生产者，处理产生的信息的部分叫做下游或者消费者。
试想如果在异步的环境中，生产者的生产速度大于消费者的消费速度的时候，明显会出现生产过剩的情景，这时候就需要消费者对多余的数据进行缓存，
但如果生产的信息数量过多，以至于超出缓存大小，就会出现缓存溢出，甚至可能造成内存耗尽。

我们可以制定一个数据丢失的规则，来丢失那些“可以丢失的数据”，以减轻缓存的压力。
在之前我们介绍了一些方法，比如`throttleXXX`、`debounce`、`sample`等，都是用来解决在生产速度过快的情况下的数据过滤的，它们指定了数据取舍的规则。
而在`Flowable`，我们可以通过`onBackpressureXXX`一系列的方法来制定当数据生产过快情况下的数据取舍的规则，

我们可以把这种处理方式理解成背压，所谓背压，在Rx中就是通过一种下游用来控制上游事件发射频率的机制（就像流体在出口受到了阻力一样）。
所以，如何理解背压呢？笔者认为，在力学中它是一种现象，在Rx中它是一种机制。

在这篇文章中，我们会先介绍背压的相关内容，然后我们再介绍一下`onBackpressureXXX`系列的方法。

关于RxJava2的基础使用和方法梳理可以参考：[RxJava2 系列 （1）：一篇的比较全面的 RxJava2 方法总结](https://juejin.im/post/5b72f76551882561354462dd)

说明：以下文章部分翻译自RxJava官方文档[Backpressure (2.0)](https://github.com/ReactiveX/RxJava/wiki/Backpressure-(2.0))。

## 1、背压机制

如果将生产和消费整体看作一个管道，生成看作上游，消费看作下游；
那么当异步的应用场景下，当生产者生产过快而消费者消费很慢的时候，可以通过背压来告知上游减慢生成的速度。

通常在进行异步的操作的时候会通过缓存来存储发射出的数据。在早期的RxJava中，这些缓存是无界的。
这意味着当需要缓存的数据非常多的时候，它们可能会占用非常多的存储空间，并有可能因为虚拟机不断GC而导致程序执行过慢，甚至直接抛出OOM。
在最新的RxJava中，大多数的异步操作内部都存在一个有界的缓存，当超出这个缓存的时候就会抛出`MissingBackpressureException`异常并结束整个序列。

然而，某些情况下的表现会有所不同，它们不会抛出`MissingBackpressureException`异常。比如下面的`range`操作：

    private static void compute(int i) throws InterruptedException {
        Thread.sleep(500);
        System.out.println("computing : " + i);
    }

    private static void testFlowable() throws InterruptedException {
        Flowable.range(1, MAX_LENGTH).observeOn(Schedulers.computation()).subscribe(FlowableTest::compute);

        Thread.sleep(500 * MAX_LENGTH);
    }

在这段代码中我们生成一段整数，然后每隔500毫秒执行依次计算操作。从输出的结果来看，在程序的实际执行过程中，数据的发射是串行的。
也就是发射完一个数据之后进入`compute`进行计算，等待500毫秒之后才发射下一个。
因此，在程序的执行过程中没有抛出异常，也没有过多的内存消耗。

而下面的这段代码就会在程序运行的时候立刻抛出`MissingBackpressureException`异常：

    PublishProcessor<Integer> source = PublishProcessor.create();
    source.observeOn(Schedulers.computation()).subscribe(v -> compute(v), Throwable::printStackTrace);
    for (int i = 0; i < 1_000_000; i++) source.onNext(i);
    Thread.sleep(10_000);

这是因为`PublishProcessor`底层会调用`PublishSubscription`，而后者实现了`AtomicLong`，它会通过判断引用的long是否为0来抛出异常，这个long型整数会在调用`PublishSubscription.request()`的时候被改写。前面的一个例子的原理就是当每次调用了观察者的`onNext`之后会调用`PublishSubscription.request()`来请求数据，这样相当于消费者会在消费完事件之后向生产者请求，因此整个序列的执行看上去是串行的，从而不会抛出异常。

## 2、onBackpressureXXX

大多数开发者在遇到`MissingBackpressureException`通常是因为使用`observeOn`方法监听了非背压的`PublishProcessor`, `timer()`， `interval()`或者自定义的`create()`。我们有以下几种方式来解决这个问题:

### 2.1 增加缓存大小

`observeOn`方法的默认缓存大小是16，当生产的速率过快的时候，那么可能很快会超出该缓存大小，从而导致缓存溢出。
一种简单的解决办法是通过提升该缓存的大小来防止缓存溢出，我们可以使用`observeOn`的重载方法来设置缓存的大小。比如：

    PublishProcessor<Integer> source = PublishProcessor.create();
    source.observeOn(Schedulers.computation(), 1024 * 1024)
          .subscribe(e -> { }, Throwable::printStackTrace);

但是这种解决方案只能解决暂时的问题，当生产的速率过快的时候还是有可能造成缓存溢出，所以这不是根本的解决办法。

### 2.2 通过丢弃和过滤来减轻缓存压力

我们可以根据自己的应用的场景和数据的重要性，选择使用一些方法来过滤和丢弃数据。
比如，丢弃的方式可以选择`throttleFirst`, `throttleLast`, `throttleWithTimeout`等，还可以使用按照时间采样的方式来减少接受的数据。

    PublishProcessor<Integer> source = PublishProcessor.create();
    source.sample(1, TimeUnit.MILLISECONDS)
          .observeOn(Schedulers.computation(), 1024)
          .subscribe(v -> compute(v), Throwable::printStackTrace);
    
但是，这种方式仅仅用来减少下游接收的数据，当缓存的数据不断增加的时候还是有可能导致缓存溢出，所以，这也不是一种根本的解决办法。

### 2.3 onBackpressureBuffer()

这种无参的方法会使用一个无界的缓存，只要虚拟机没有抛出OOM异常，它就会把所有的数据缓存起来。

     Flowable.range(1, 1_000_000)
               .onBackpressureBuffer()
               .observeOn(Schedulers.computation(), 8)
               .subscribe(e -> { }, Throwable::printStackTrace);

上面的例子即使使用了很小的缓存也不会有异常抛出，因为`onBackpressureBuffer`会将发射的所有数据缓存起来，只会将一小部分的数据传递给`observeOn`。

这种处理方式实际上是不存在背压的，因为`onBackpressureBuffer`缓存了所有的数据，我们可以使用该方法的4个重载方法来对背压进行个性化设置。

### 2.4 onBackpressureBuffer(int capacity)

这个方法使用一个有界的缓存，当达到了缓存大小的时候会抛出一个`BufferOverflowError`错误。
通过这种方法可以增加默认的缓存大小，但是通过`observeOn`方法一样可以指定缓存的大小，因此，这个方法的应用变得越来越少。

### 2.5 onBackpressureBuffer(int capacity, Action onOverflow)

这方法除了可以指定一个有界的缓存还提供了一个，当缓存溢出的时候还会回调指定的Action。
但是这种回调的用途比较有限，因为它除了提供当前回调的栈信息以外提供不了任何有用的信息。

### 2.6 onBackpressureBuffer(int capacity, Action onOverflow, BackpressureOverflowStrategy strategy)

这个重载方法相对比较实用一些，它除了上面的那些功能之外，还指定了当缓存到达指定的缓存时的行为。
这里的`BackpressureOverflowStrategy`顾名思义是一个策略，它是一个枚举类型，预定义了三种枚举值，最终会在`FlowableOnBackpressureBufferStrategy`中根据指定的枚举类型选择不同的实现策略，因此，我们可以使用它来指定缓存溢出时候的行为。

下面是该枚举类型的三个值及其含义：

1. `ERROR`：当缓存溢出的时候会抛出一个异常；
2. `DROP_OLDEST`：当缓存发生溢出的时候，会丢弃最老的值，并将新的值插入到缓存中；
3. `DROP_LATEST`：当缓存发生溢出的时候，最新的值会被忽略，只有比较老的值会被传递给下游使用；

需要注意的地方是，后面的两种策略会造成下游获取到的值是不连续的，因为有一部分值会因为缓存不够被丢弃，但是它们不会抛出`BufferOverflowException`。

### 2.7 onBackpressureDrop()

这个方法会在数据达到缓存大小的时候丢弃最新的数据。可以将其看成是`onBackpressureBuffer`+`0 capacity`+`DROP_LATEST`的组合。

这个方法特别适用于那种可以忽略从源中发射出值的那种场景，比如GPS定位问题，定位数据会不断发射出来，即使丢失当前数据，等会儿一样能拿到最新的数据。

    component.mouseMoves()
        .onBackpressureDrop()
        .observeOn(Schedulers.computation(), 1)
        .subscribe(event -> compute(event.x, event.y));

该方法还存在一个重载方法`onBackpressureDrop(Consumer<? super T> onDrop)`，它允许我们传入一个接口来指定当某个数据被丢失时的行为。

### 2.8 onBackpressureLatest()

对应于`onBackpressureDrop()`的，还有`onBackpressureLatest()`方法，该方法只会保留最新的数据并会覆盖较老、没有分发的数据。
我们可以将其看成是`onBackpressureBuffer`+`1 capacity`+`DROP_OLDEST`的组合。

与`onBackpressureDrop()`不同的地方在于，当下游消费过慢的时候，这种方式总会存在一个缓存的值。
这种特别适用于那种数据的生产非常频繁，但是只有最新的数据会被消费的那种情形。比如，当用户点击了屏幕，那么我们倾向于只处理最新按下的位置的事件。

    component.mouseClicks()
        .onBackpressureLatest()
        .observeOn(Schedulers.computation())
        .subscribe(event -> compute(event.x, event.y), Throwable::printStackTrace);

所以，总结一下：

1. `onBackpressureDrop()`：不会缓存任何数据，专注于当下，新来的数据来不及处理就丢掉，以后会有更好的；
2. `onBackpressureLatest()`：会缓存一个数据，当正在执行某个任务的时候有新的数据过来，会把它缓存起来，如果又有新的数据过来，那就把之前的替换掉，缓存里面的总是最新的。

## 3、总结

以上就是背压机制的一些内容，以及我们介绍了`Flowable`中的几个背压相关的方法。
实际上，RxJava的官方文档也有说明——`Flowable`适用于数据量比较大的情景，因为它的一些创建方法本身就使用了背压机制。
这部分方法我们就不再一一进行说明，因为，它们的方法签名和`Observable`基本一致，只是多了一层背压机制。

比较匆匆地整理完了背压的内容，但是我想这块还会有更加丰富的内容值得我们去发现和探索。

以上。