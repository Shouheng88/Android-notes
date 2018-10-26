# RxJava2 系列 （1）：一篇的比较全面的 RxJava2 方法总结

看了许多讲解RxJava的文章，有些文章讲解的内容是基于第一个版本的，有些文章的讲解是通过比较常用的一些API和基础的概念进行讲解的。
但是每次看到RxJava的类中的几十个方法的时候，总是感觉心里没底。所以，我打算自己去专门写篇文章来从API的角度系统地梳理一下RxJava的各种方法和用法。

## 1、RxJava 基本

### 1.1 RxJava 简介

RxJava是一个在Java VM上使用可观测的序列来组成异步的、基于事件的程序的库。

虽然，在Android中，我们可以使用AsyncTask来完成异步任务操作，但是当任务的梳理比较多的时候，我们要为每个任务定义一个AsyncTask就变得非常繁琐。
RxJava能帮助我们在实现异步执行的前提下保持代码的清晰。
它的原理就是创建一个`Observable`来完成异步任务，组合使用各种不同的链式操作，来实现各种复杂的操作，最终将任务的执行结果发射给`Observer`进行处理。
当然，RxJava不仅适用于Android，也适用于服务端等各种场景。

我们总结以下RxJava的用途：

1. 简化异步程序的流程；
2. 使用近似于Java8的流的操作进行编程：因为想要在Android中使用Java8的流编程有诸多的限制，所以我们可以使用RxJava来实现这个目的。

在使用RxJava之前，我们需要先在自己的项目中添加如下的依赖：

    compile 'io.reactivex.rxjava2:rxjava:2.2.0'
    compile 'io.reactivex.rxjava2:rxandroid:2.0.2'

这里我们使用的是RxJava2，它与RxJava的第一个版本有些许不同。在本文中，我们所有的关于RxJava的示例都将基于RxJava2. 

注：如果想了解关于Java8的流编程的内容的内容，可以参考我之前写过的文章[五分钟学习Java8的流编程](https://juejin.im/post/5b07f4536fb9a07ac90da4e5)。

### 1.2 概要

下面是RxJava的一个基本的用例，这里我们定义了一个`Observable`，然后在它内部使用`emitter`发射了一些数据和信息（其实就相当于调用了被观察对象内部的方法，通知所有的观察者）。
然后，我们用`Consumer`接口的实例作为`subscribe()`方法的参数来观察发射的结果。（这里的接口的方法都已经被使用Lambda简化过，应该学着适应它。）

    Observable<Integer> observable = Observable.create(emitter -> {
        emitter.onNext(1);
        emitter.onNext(2);
        emitter.onNext(3);
    });
    observable.subscribe(System.out::println);

这样，我们就完成了一个基本的RxJava的示例。从上面的例子中，你或许没法看出`Observable`中隐藏的流的概念，看下面的例子：

    Observable.range(0, 10).map(String::valueOf).forEach(System.out::println);

这里我们先用`Observable.range()`方法产生一个序列，然后用`map`方法将该整数序列映射成一个字符序列，最后将得到的序列输出来。从上面看出，这种操作和Java8里面的Stream编程很像。但是两者之间是有区别的：

1. 所谓的“推”和“拉”的区别：Stream中是通过从流中读取数据来实现链式操作，而RxJava除了Stream中的功能之外，还可以通过“发射”数据，来实现通知的功能，即RxJava在Stream之上又多了一个观察者的功能。
2. Java8中的Stream可以通过`parall()`来实现并行，即基于分治算法将任务分解并计算得到结果之后将结果合并起来；而RxJava只能通过`subscribeOn()`方法将所有的操作切换到某个线程中去。
3. Stream只能被消费一次，但是`Observable`可以被多次进行订阅；

RxJava除了为我们提供了`Observable`之外，在新的RxJava中还提供了适用于其他场景的基础类，它们之间的功能和主要区别如下：

1. `Flowable`: 多个流，响应式流和背压
2. `Observable`: 多个流，无背压
3. `Single`: 只有一个元素或者错误的流
4. `Completable`: 没有任何元素，只有一个完成和错误信号的流
5. `Maybe`: 没有任何元素或者只有一个元素或者只有一个错误的流

除了上面的几个基础类之外，还有一个`Disposable`。当我们监听某个流的时候，就能获取到一个`Disposable`对象。它提供了两个方法，一个是`isDisposed`，可以被用来判断是否停止了观察指定的流；另一个是`dispose`方法，用来放弃观察指定的流，我们可以使用它在任意的时刻停止观察操作。

### 1.3 总结

上面我们介绍了了关于RxJava的基本的概念和使用方式，在下面的文章中我们会按照以上定义的顺序从API的角度来讲解以下RxJava各个模块的使用方法。

## 2、RxJava 的使用

### 2.1 Observable

从上面的文章中我们可以得知，`Observable`和后面3种操作功能近似，区别在于`Flowable`加入了背压的概念，`Observable`的大部分方法也适用于其他3个操作和`Flowable`。
因此，我们这里先从`Observable`开始梳理，然后我们再专门对`Flowable`和背压的进行介绍。

`Observable`为我们提供了一些静态的构造方法来创建一个`Observable`对象，还有许多链式的方法来完成各种复杂的功能。
这里我们按照功能将它的这些方法分成各个类别并依次进行相关的说明。

#### 2.1.1 创建操作

1.interval & intervalRange

下面的操作可以每个3秒的时间发送一个整数，整数从0开始：

    Observable.interval(3, TimeUnit.SECONDS).subscribe(System.out::println);

如果想要设置从指定的数字开始也是可以的，实际上`interval`提供了许多重载方法供我们是使用。下面我们连同与之功能相近的`intervalRange`方法也一同给出：

1. `public static Observable<Long> interval(long initialDelay, long period, TimeUnit unit, Scheduler scheduler)`
2. `public static Observable<Long> interval(long period, TimeUnit unit, Scheduler scheduler)`
3. `public static Observable<Long> intervalRange(long start, long count, long initialDelay, long period, TimeUnit unit, Scheduler scheduler)`

这里的`initialDelay`参数用来指示开始发射第一个整数的之前要停顿的时间，时间的单位与`peroid`一样，都是通过`unit`参数来指定的；`period`参数用来表示每个发射之间停顿多少时间；`unit`表示时间的单位，是`TimeUnit`类型的；`scheduler`参数指定数据发射和等待时所在的线程。

`intervalRange`方法可以用来将发射的整数序列限制在一个范围之内，这里的`start`用来表示发射的数据的起始值，`count`表示总共要发射几个数字，其他参数与上面的`interval`方法一致。

2.range & rangeLong

下面的操作可以产生一个从5开始的连续10个整数构成的序列：

    Observable.range(5, 10).subscribe(i -> System.out.println("1: " + i));

该方法需要传入两个参数，与之有相同功能的方法还有`rangeLong`：

1. `public static Observable<Integer> range(final int start, final int count)`
2. `public static Observable<Long> rangeLong(long start, long count)`

这里的两个参数`start`用来指定用于生成的序列的开始值，`count`用来指示要生成的序列总共包含多少个数字，上面的两个方法的主要区别在于一个是用来生成int型整数的，一个是用来生成long型整数的。

3.create

`create`方法用于从头开始创建一个`Observable`，像下面显示的那样，你需要使用`create`方法并传一个发射器作为参数，在该发射器内部调用`onNext`、`onComplete`和`onError`方法就可以将数据发送给监听者。

    Observable.create((ObservableOnSubscribe<Integer>) observableEmitter -> {
        observableEmitter.onNext(1);
        observableEmitter.onNext(2);
        observableEmitter.onComplete();
    }).subscribe(System.out::println);

4.defer

`defer`直到有观察者订阅时才创建Observable，并且为每个观察者创建一个新的Observable。`defer`操作符会一直等待直到有观察者订阅它，然后它使用Observable工厂方法生成一个Observable。比如下面的代码两个订阅输出的结果是不一致的：

    Observable<Long> observable = Observable.defer((Callable<ObservableSource<Long>>) () -> Observable.just(System.currentTimeMillis()));
    observable.subscribe(System.out::print);
    System.out.println();
    observable.subscribe(System.out::print);

下面是该方法的定义，它接受一个Callable对象，可以在该对象中返回一个Observable的实例：

`public static <T> Observable<T> defer(Callable<? extends ObservableSource<? extends T>> supplier)`

5.empty & never & error

1. `public static <T> Observable<T> empty()`：创建一个不发射任何数据但是正常终止的Observable；
2. `public static <T> Observable<T> never()`：创建一个不发射数据也不终止的Observable；
3. `public static <T> Observable<T> error(Throwable exception)`：创建一个不发射数据以一个错误终止的Observable，它有几个重载版本，这里给出其中的一个。

测试代码：

    Observable.empty().subscribe(i->System.out.print("next"),i->System.out.print("error"),()->System.out.print("complete"));
    Observable.never().subscribe(i->System.out.print("next"),i->System.out.print("error"),()->System.out.print("complete"));
    Observable.error(new Exception()).subscribe(i->System.out.print("next"),i->System.out.print("error"),()->System.out.print("complete"));

输出结果：`completeerror`

6.from 系列

`from`系列的方法用来从指定的数据源中获取一个Observable：

1. `public static <T> Observable<T> fromArray(T... items)`：从数组中获取；
2. `public static <T> Observable<T> fromCallable(Callable<? extends T> supplier)`：从Callable中获取；
3. `public static <T> Observable<T> fromFuture(Future<? extends T> future)`：从Future中获取，有多个重载版本，可以用来指定线程和超时等信息；
4. `public static <T> Observable<T> fromIterable(Iterable<? extends T> source)`：从Iterable中获取；
5. `public static <T> Observable<T> fromPublisher(Publisher<? extends T> publisher)`：从Publisher中获取。

7.just 系列

just系列的方法的一个参数的版本为下面的形式：`public static <T> Observable<T> just(T item)`，它还有许多个重载的版本，区别在于接受的参数的个数不同，最少1个，最多10个。

8.repeat

该方法用来表示指定的序列要发射多少次，下面的方法会将该序列无限次进行发送：

    Observable.range(5, 10).repeat().subscribe(i -> System.out.println(i));

`repeat`方法有以下几个相似方法：

1. `public final Observable<T> repeat()`
2. `public final Observable<T> repeat(long times)`
3. `public final Observable<T> repeatUntil(BooleanSupplier stop)`
4. `public final Observable<T> repeatWhen(Function<? super Observable<Object>, ? extends ObservableSource<?>> handler)`

第1个无参的方法会无限次地发送指定的序列（实际上内部调用了第2个方法并传入了Long.MAX_VALUE），第2个方法会将指定的序列重复发射指定的次数；第3个方法会在满足指定的要求的时候停止重复发送，否则会一直发送。

9.timer

timer操作符创建一个在给定的时间段之后返回一个特殊值的Observable，它在延迟一段给定的时间后发射一个简单的数字0。比如下面的程序会在500毫秒之后输出一个数字`0`。

    Observable.timer(500, TimeUnit.MILLISECONDS).subscribe(System.out::print);

下面是该方法及其重载方法的定义，重载方法还可以指定一个调度器：

1. `public static Observable<Long> timer(long delay, TimeUnit unit)`
2. `public static Observable<Long> timer(long delay, TimeUnit unit, Scheduler scheduler)`

#### 2.1.2 变换操作

1.map & cast

1. `map`操作符对原始Observable发射的每一项数据应用一个你选择的函数，然后返回一个发射这些结果的Observable。默认不在任何特定的调度器上执行。
2. `cast`操作符将原始Observable发射的每一项数据都强制转换为一个指定的类型（多态），然后再发射数据，它是map的一个特殊版本：

下面的第一段代码用于将生成的整数序列转换成一个字符串序列之后并输出；第二段代码用于将Date类型转换成Object类型并进行输出，这里如果前面的Class无法转换成第二个Class就会出现异常：

    Observable.range(1, 5).map(String::valueOf).subscribe(System.out::println);
    Observable.just(new Date()).cast(Object.class).subscribe(System.out::print);

这两个方法的定义如下：

1. `public final <R> Observable<R> map(Function<? super T, ? extends R> mapper)`
2. `public final <U> Observable<U> cast(Class<U> clazz)`

这里的`mapper`函数接受两个泛型，一个表示原始的数据类型，一个表示要转换之后的数据类型，转换的逻辑写在该接口实现的方法中即可。

2.flatMap & contactMap

`flatMap`将一个发送事件的上游Observable变换为多个发送事件的Observables，然后将它们发射的事件合并后放进一个单独的Observable里。需要注意的是, flatMap并不保证事件的顺序，也就是说转换之后的Observables的顺序不必与转换之前的序列的顺序一致。比如下面的代码用于将一个序列构成的整数转换成多个单个的`Observable`，然后组成一个`OBservable`，并被订阅。下面输出的结果仍将是一个字符串数字序列，只是顺序不一定是增序的。

    Observable.range(1, 5)
            .flatMap((Function<Integer, ObservableSource<String>>) i -> Observable.just(String.valueOf(i)))
            .subscribe(System.out::println);

与`flatMap`对应的方法是`contactMap`，后者能够保证最终输出的顺序与上游发送的顺序一致。下面是这两个方法的定义：

1. `public final <R> Observable<R> flatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper)`
2. `public final <R> Observable<R> concatMap(Function<? super T, ? extends ObservableSource<? extends R>> mapper)`

`flatMap`的重载方法数量过多，它们在数据源方面略有不同，有的支持错误等可选参数，具体可以参考源代码。

3.flatMapIterable

`flatMapIterable`可以用来将上流的任意一个元素转换成一个`Iterable`对象，然后我们可以对其进行消费。在下面的代码中，我们先生成一个整数的序列，然后将每个整数映射成一个`Iterable<string>`类型，最后，我们对其进行订阅和消费：

    Observable.range(1, 5)
            .flatMapIterable((Function<Integer, Iterable<String>>) integer -> Collections.singletonList(String.valueOf(integer)))
            .subscribe(s -> System.out.println("flatMapIterable : " + s));

下面是该方法及其重载方法的定义：

1. `public final <U> Observable<U> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> mapper)`
2. `public final <U, V> Observable<V> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends V> resultSelector)`

4.buffer

该方法用于将整个流进行分组。以下面的程序为例，我们会先生成一个7个整数构成的流，然后使用`buffer`之后，这些整数会被3个作为一组进行输出，所以当我们订阅了`buffer`转换之后的`Observable`之后得到的是一个列表构成的`OBservable`：

    Observable.range(1, 7).buffer(3)
            .subscribe(integers -> System.out.println(Arrays.toString(integers.toArray())));

下面是这个方法及其重载方法的定义，它的重载方法太多，这里我们只给出其中的两个，其他的可以参考RxJava的源码。这里的buffer应该理解为一个缓冲区，当缓冲区满了或者剩余的数据不够一个缓冲区的时候就将数据发射出去。

1. `public final Observable<List<T>> buffer(int count)`
2. `public final Observable<List<T>> buffer(int count, int skip)`
3. ...

5.groupBy

`groupBy`用于分组元素，它可以被用来根据指定的条件将元素分成若干组。它将得到一个`Observable<GroupedObservable<T, M>>`类型的`Observable`。如下面的程序所示，这里我们使用`concat`方法先将两个`Observable`拼接成一个`Observable`，然后对其元素进行分组。这里我们的分组依据是整数的值，这样我们将得到一个`Observable<GroupedObservable<Integer, Integer>>`类型的`Observable`。然后，我们再将得到的序列拼接成一个并进行订阅输出：

    Observable<GroupedObservable<Integer, Integer>> observable = Observable.concat(
            Observable.range(1,4), Observable.range(1,6)).groupBy(integer -> integer);
    Observable.concat(observable).subscribe(integer -> System.out.println("groupBy : " + integer));

该方法有多个重载版本，这里我们用到的一个的定义是：

`public final <K> Observable<GroupedObservable<K, T>> groupBy(Function<? super T, ? extends K> keySelector)`

6.scan

`scan`操作符对原始Observable发射的第一项数据应用一个函数，然后将那个函数的结果作为自己的第一项数据发射。它将函数的结果同第二项数据一起填充给这个函数来产生它自己的第二项数据。它持续进行这个过程来产生剩余的数据序列。这个操作符在某些情况下被叫做accumulator。

以下面的程序为例，该程序的输结果是`2 6 24 120 720`，可以看出这里的计算规则是，我们把传入到`scan`中的函数记为`f`，序列记为`x`，生成的序列记为`y`，那么这里的计算公式是`y(0)=x(0); y(i)=f(y(i-1), x(i)), i>0`：

    Observable.range(2, 5).scan((i1, i2) -> i1 * i2).subscribe(i -> System.out.print(i + " "));

除了上面的这种形式，`scan`方法还有一个重载的版本，我们可以使用这个版本的方法来在生成序列的时候指定一个初始值。以下面的程序为例，它的输出结果是`3 6 18 72 360 2160 `，可以看出它的输出比上面的形式多了1个，这是因为当指定了初始值之后，生成的第一个数字就是那个初始值，剩下的按照我们上面的规则进行的。所以，用同样的函数语言来描述的话，那么它就应该是下面的这种形式：`y(0)=initialValue; y(i)=f(y(i-1), x(i)), i>0`。

    Observable.range(2, 5).scan(3, (i1, i2) -> i1 * i2).subscribe(i -> System.out.print(i + " "));

以上方法的定义是：

1. `public final Observable<T> scan(BiFunction<T, T, T> accumulator)`
2. `public final <R> Observable<R> scan(R initialValue, BiFunction<R, ? super T, R> accumulator)`

7.window

`window`Window和Buffer类似，但不是发射来自原始Observable的数据包，它发射的是Observable，这些Observables中的每一个都发射原始Observable数据的一个子集，最后发射一个onCompleted通知。

以下面的程序为例，这里我们首先生成了一个由10个数字组成的整数序列，然后使用`window`函数将它们每3个作为一组，每组会返回一个对应的Observable对象。
这里我们对该返回的结果进行订阅并进行消费，因为10个数字，所以会被分成4个组，每个对应一个Observable：

    Observable.range(1, 10).window(3).subscribe(
            observable -> observable.subscribe(integer -> System.out.println(observable.hashCode() + " : " + integer)));

除了对数据包进行分组，我们还可以根据时间来对发射的数据进行分组。该方法有多个重载的版本，这里我们给出其中的比较具有代表性的几个：

1. `public final Observable<Observable<T>> window(long count)`
2. `public final Observable<Observable<T>> window(long timespan, long timeskip, TimeUnit unit)`
3. `public final <B> Observable<Observable<T>> window(ObservableSource<B> boundary)`
4. `public final <B> Observable<Observable<T>> window(Callable<? extends ObservableSource<B>> boundary)`

#### 2.1.3 过滤操作

1.filter

`filter`用来根据指定的规则对源进行过滤，比如下面的程序用来过滤整数1到10中所有大于5的数字：

    Observable.range(1,10).filter(i -> i > 5).subscribe(System.out::println);

下面是该方法的定义：

1. `public final Observable<T> filter(Predicate<? super T> predicate)`

2.elementAt & firstElement & lastElement

`elementAt`用来获取源中指定位置的数据，它有几个重载方法，这里我们介绍一下最简单的一个方法的用法。下面是`elementAt`的一个示例，它将获取源数据中索引为1的元素并交给观察者订阅。下面的程序将输出`1`

    Observable.range(1, 10).elementAt(0).subscribe(System.out::print);

这里我们给出`elementAt`及其相关的方法的定义，它们的使用相似。注意一下这里的返回类型：

1. `public final Maybe<T> elementAt(long index)`
2. `public final Single<T> elementAt(long index, T defaultItem)`
3. `public final Single<T> elementAtOrError(long index)`

除了获取指定索引的元素的方法之外，RxJava中还有可以用来直接获取第一个和最后一个元素的方法，这里我们直接给出方法的定义：

1. `public final Maybe<T> firstElement()`
2. `public final Single<T> first(T defaultItem)`
3. `public final Single<T> firstOrError()`
4. `public final Maybe<T> lastElement()`
5. `public final Single<T> last(T defaultItem)`
6. `public final Single<T> lastOrError()`

3.distinct & distinctUntilChanged

`distinct`用来对源中的数据进行过滤，以下面的程序为例，这里会把重复的数字7过滤掉：

    Observable.just(1,2,3,4,5,6,7,7).distinct().subscribe(System.out::print);

与之类似的还有`distinctUntilChanged`方法，与`distinct`不同的是，它只当相邻的两个元素相同的时候才会将它们过滤掉。比如下面的程序会过滤掉其中的2和5，所以最终的输出结果是`12345676`：

    Observable.just(1,2,2,3,4,5,5,6,7,6).distinctUntilChanged().subscribe(System.out::print);

该方法也有几个功能相似的方法，这里给出它们的定义如下：

1. `public final Observable<T> distinct()`
2. `public final <K> Observable<T> distinct(Function<? super T, K> keySelector)`
3. `public final <K> Observable<T> distinct(Function<? super T, K> keySelector, Callable<? extends Collection<? super K>> collectionSupplier)`
4. `public final Observable<T> distinctUntilChanged()`
5. `public final <K> Observable<T> distinctUntilChanged(Function<? super T, K> keySelector)`
6. `public final Observable<T> distinctUntilChanged(BiPredicate<? super T, ? super T> comparer)`

4.skip & skipLast & skipUntil & skipWhile

`skip`方法用于过滤掉数据的前n项，比如下面的程序将会过滤掉前2项，因此输出结果是`345`：

    Observable.range(1, 5).skip(2).subscribe(System.out::print);

与`skip`方法对应的是`take`方法，它用来表示只选择数据源的前n项，该方法的示例就不给出了。这里，我们说一下与之类功能类似的重载方法。`skip`还有一个重载方法接受两个参数，用来表示跳过指定的时间，也就是在指定的时间之后才开始进行订阅和消费。下面的程序会在3秒之后才开始不断地输出数字：

    Observable.range(1,5).repeat().skip(3, TimeUnit.SECONDS).subscribe(System.out::print);

与`skip`功能相反的方法的还有`skipLast`，它用来表示过滤掉后面的几项，以及最后的一段时间不进行发射等。比如下面的方法，我们会在程序开始之前进行计时，然后会不断重复输出数字，直到5秒之后结束。然后，我们用`skipLast`方法表示最后的2秒不再进行发射。所以下面的程序会先不断输出数字3秒，3秒结束后停止输出，并在2秒之后结束程序：

    long current = System.currentTimeMillis();
    Observable.range(1,5)
            .repeatUntil(() -> System.currentTimeMillis() - current > TimeUnit.SECONDS.toMillis(5))
            .skipLast(2, TimeUnit.SECONDS).subscribe(System.out::print);

与上面的这些方法类似的还有一些，这里我们不再一一列举。因为这些方法的重载方法比较多，下面我们给出其中的具有代表性的一部分：

1. `public final Observable<T> skip(long count)`
2. `public final Observable<T> skip(long time, TimeUnit unit, Scheduler scheduler)`
3. `public final Observable<T> skipLast(int count)`
4. `public final Observable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize)`
5. `public final <U> Observable<T> skipUntil(ObservableSource<U> other)`
6. `public final Observable<T> skipWhile(Predicate<? super T> predicate)`

5.take & takeLast & takeUntil & takeWhile

与`skip`方法对应的是`take`方法，它表示按照某种规则进行选择操作。我们以下面的程序为例，这里第一段程序表示只发射序列中的前2个数据：

    Observable.range(1, 5).take(2).subscribe(System.out::print);

下面的程序表示只选择最后2秒中输出的数据：

    long current = System.currentTimeMillis();
    Observable.range(1,5)
            .repeatUntil(() -> System.currentTimeMillis() - current > TimeUnit.SECONDS.toMillis(5))
            .takeLast(2, TimeUnit.SECONDS).subscribe(System.out::print);

下面是以上相关的方法的定义，同样的，我们只选择其中比较有代表性的几个：

1. `public final Observable<T> take(long count)`
2. `public final Observable<T> takeLast(long count, long time, TimeUnit unit, Scheduler scheduler, boolean delayError, int bufferSize)`
3. `public final <U> Observable<T> takeUntil(ObservableSource<U> other)`
4. `public final Observable<T> takeUntil(Predicate<? super T> stopPredicate)`
5. `public final Observable<T> takeWhile(Predicate<? super T> predicate)`

6.ignoreElements

该方法用来过滤所有源Observable产生的结果，只会把Observable的onComplete和onError事件通知给订阅者。下面是该方法的定义：

1. `public final Completable ignoreElements()`

7.throttleFirst & throttleLast & throttleLatest & throttleWithTimeout

这些方法用来对输出的数据进行限制，它们是通过时间的”窗口“来进行限制的，你可以理解成按照指定的参数对时间进行分片，然后根据各个方法的要求选择第一个、最后一个、最近的等进行发射。下面是`throttleLast`方法的用法示例，它会输出每个500毫秒之间的数字中最后一个数字：

    Observable.interval(80, TimeUnit.MILLISECONDS)
            .throttleLast(500, TimeUnit.MILLISECONDS)
            .subscribe(i -> System.out.print(i + " "));

其他的几个方法的功能大致列举如下：

1. `throttleFirst`只会发射指定的Observable在指定的事件范围内发射出来的第一个数据；
2. `throttleLast`只会发射指定的Observable在指定的事件范围内发射出来的最后一个数据；
3. `throttleLatest`用来发射距离指定的时间分片最近的那个数据;
5. `throttleWithTimeout`仅在过了一段指定的时间还没发射数据时才发射一个数据，如果在一个时间片达到之前，发射的数据之后又紧跟着发射了一个数据，那么这个时间片之内之前发射的数据会被丢掉，该方法底层是使用`debounce`方法实现的。如果数据发射的频率总是快过这里的`timeout`参数指定的时间，那么将不会再发射出数据来。

下面是这些方法及其重载方法的定义（选择其中一部分）：

1. `public final Observable<T> throttleFirst(long skipDuration, TimeUnit unit, Scheduler scheduler)`
2. `public final Observable<T> throttleLast(long intervalDuration, TimeUnit unit, Scheduler scheduler)`
3. `public final Observable<T> throttleLatest(long timeout, TimeUnit unit, Scheduler scheduler, boolean emitLast)`
4. `public final Observable<T> throttleWithTimeout(long timeout, TimeUnit unit, Scheduler scheduler)`

8.debounce 

`debounce`也是用来限制发射频率过快的，它仅在过了一段指定的时间还没发射数据时才发射一个数据。我们通过下面的图来说明这个问题：

![debounce](https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.png)

这里红、绿、蓝三个球发射出来的原因都是因为当反射了这个球之后的一定的时间内没有其他的球发射出来，这个时间是我们可以通过参数来指定的。

该方法的用法与`throttle`之类的方法类似，上面也说过`throttle`那些方法底层用了`debounce`实现，所以，这里我们不再为该方法专门编写相关的测试代码。

9.sample

实际上`throttleLast`的实现中内部调用的就是`sample`。

#### 2.1.4 组合操作

1.startWith & startWithArray

`startWith`方法可以用来在指定的数据源的之前插入几个数据，它的功能类似的方法有`startWithArray`，另外还有几个重载方法。这里我们给出一个基本的用法示例，下面的程序会在原始的数字流1-5的前面加上0，所以最终的输出结果是`012345`：

    Observable.range(1,5).startWith(0).subscribe(System.out::print);

下面是`startWith`及其几个功能相关的方法的定义：

1. `public final Observable<T> startWith(Iterable<? extends T> items)`
2. `public final Observable<T> startWith(ObservableSource<? extends T> other)`
3. `public final Observable<T> startWith(T item)`
4. `public final Observable<T> startWithArray(T... items)`

2.merge & mergeArray

`merge`可以让多个数据源的数据合并起来进行发射，当然它可能会让`merge`之后的数据交错发射。下面是一个示例，这个例子中，我们使用`merge`方法将两个`Observable`合并到了一起进行监听：

    Observable.merge(Observable.range(1,5), Observable.range(6,5)).subscribe(System.out::print);

鉴于`merge`方法及其功能类似的方法太多，我们这里挑选几个比较有代表性的方法，具体的可以查看RxJava的源代码：

1. `public static <T> Observable<T> merge(Iterable<? extends ObservableSource<? extends T>> sources)`
2. `public static <T> Observable<T> mergeArray(ObservableSource<? extends T>... sources)`
3. `public static <T> Observable<T> mergeDelayError(Iterable<? extends ObservableSource<? extends T>> sources)`
4. `public static <T> Observable<T> mergeArrayDelayError(ObservableSource<? extends T>... sources)`

这里的`mergeError`方法与`merge`方法的表现一致，只是在处理由`onError`触发的错误的时候有所不同。`mergeError`方法会等待所有的数据发射完毕之后才把错误发射出来，即使多个错误被触发，该方法也只会发射出一个错误信息。而如果使用`merger`方法，那么当有错误被触发的时候，该错误会直接被抛出来，并结束发射操作。下面是该方法的一个使用的示例，这里我们主线程停顿4秒，然后所有`merge`的Observable中的一个会在线程开始的第2秒的时候触发一个错误，该错误最终会在所有的数据发射完毕之后被发射出来：

    Observable.mergeDelayError(Observable.range(1,5),
            Observable.range(1,5).repeat(2),
            Observable.create((ObservableOnSubscribe<String>) observableEmitter -> {
                Thread.sleep(2000);
                observableEmitter.onError(new Exception("error"));
            })
    ).subscribe(System.out::print, System.out::print);
    Thread.sleep(4000);

3.concat & concatArray & concatEager

该方法也是用来将多个Observable拼接起来，但是它会严格按照传入的Observable的顺序进行发射，一个Observable没有发射完毕之前不会发射另一个Observable里面的数据。下面是一个程序示例，这里传入了两个Observable，会按照顺序输出`12345678910`：

    Observable.concat(Observable.range(1, 5), Observable.range(6, 5)).subscribe(System.out::print);

下面是该方法的定义，鉴于该方法及其重载方法太多，这里我们选择几个比较有代表性的说明：

1. `public static <T> Observable<T> concat(Iterable<? extends ObservableSource<? extends T>> sources)`
2. `public static <T> Observable<T> concatDelayError(Iterable<? extends ObservableSource<? extends T>> sources)`
3. `public static <T> Observable<T> concatArray(ObservableSource<? extends T>... sources)`
4. `public static <T> Observable<T> concatArrayDelayError(ObservableSource<? extends T>... sources)`
5. `public static <T> Observable<T> concatEager(ObservableSource<? extends ObservableSource<? extends T>> sources)`
6. `public static <T> Observable<T> concatArrayEager(ObservableSource<? extends T>... sources)`

对于`concat`方法，我们之前已经介绍过它的用法；这里的`conactArray`的功能与之类似；对于`concatEager`方法，当一个观察者订阅了它的结果，那么就相当于订阅了它拼接的所有`ObservableSource`，并且会先缓存这些ObservableSource发射的数据，然后再按照顺序将它们发射出来。而对于这里的`concatDelayError`方法的作用和前面的`mergeDelayError`类似，只有当所有的数据都发射完毕才会处理异常。

4.zip & zipArray & zipIterable

`zip`操作用来将多个数据项进行合并，可以通过一个函数指定这些数据项的合并规则。比如下面的程序的输出结果是`6 14 24 36 50 `，显然这里的合并的规则是相同索引的两个数据的乘积。不过仔细看下这里的输出结果，可以看出，如果一个数据项指定的位置没有对应的值的时候，它是不会参与这个变换过程的：

    Observable.zip(Observable.range(1, 6), Observable.range(6, 5), (integer, integer2) -> integer * integer2)
            .subscribe(i -> System.out.print(i + " "));

`zip`方法有多个重载的版本，同时也有功能近似的方法，这里我们挑选有代表性的几个进行说明：

1. `public static <T, R> Observable<R> zip(Iterable<? extends ObservableSource<? extends T>> sources, Function<? super Object[], ? extends R> zipper)`
2. `ublic static <T, R> Observable<R> zipArray(Function<? super Object[], ? extends R> zipper, boolean delayError, int bufferSize, ObservableSource... sources)`
3. `public static <T, R> Observable<R> zipIterable(Iterable<? extends ObservableSource<? extends T>> sources, Function<? super Object[], ? extends R> zipper, boolean delayError, int bufferSize)`

实际上上面几个方法的用法和功能基本类似，区别在于传入的`ObservableSource`的参数的形式。

5.combineLastest

与`zip`操作类似，但是这个操作的输出结果与`zip`截然不同，以下面的程序为例，它的输出结果是`36 42 48 54 60`：

    Observable.combineLatest(Observable.range(1, 6), Observable.range(6, 5), (integer, integer2) -> integer * integer2)
            .subscribe(i -> System.out.print(i + " "));

利用下面的这张图可以比较容易来说明这个问题：

![combineLastest](https://github.com/Shouheng88/Awesome-Android/blob/master/%E5%93%8D%E5%BA%94%E5%BC%8F%E7%BC%96%E7%A8%8B/res/combineLatest.png?raw=true)

上图中的上面的两条横线代表用于拼接的两个数据项，下面的一条横线是拼接之后的结果。`combineLatest`的作用是拼接最新发射的两个数据。下面我们用上图的过程来说明该方法是如何执行的：开始第一条只有1的时候无法拼接，；当第二条出现A的时候，此时最新的数据是1和A，故组合成一个1A；第二个数据项发射了B，此时最新的数据是1和B，故组合成1B；第一条横线发射了2，此时最新的数据是2和B，因此得到了2B，依次类推。然后再回到我们上面的问题，第一个数据项连续发射了5个数据的时候，第二个数据项一个都没有发射出来，因此没有任何输出；然后第二个数据项开始发射数据，当第二个数据项发射了6的时候，此时最新的数据组合是6和6，故得36；然后，第二个数据项发射了7，此时最新的数据组合是6和7，故得42，依次类推。

该方法也有对应的`combineLatestDelayError`方法，用途也是只有当所有的数据都发射完毕的时候才去处理错误逻辑。

#### 2.1.5 辅助操作

1.delay

`delay`方法用于在发射数据之前停顿指定的时间，比如下面的程序会在真正地发射数据之前停顿1秒：

    Observable.range(1, 5).delay(1000, TimeUnit.MILLISECONDS).subscribe(System.out::print);
    Thread.sleep(1500);

同样`delay`方法也有几个重载的方法，可以供我们用来指定触发的线程等信息，这里给出其中的两个，其他的可以参考源码和文档：

1. `public final Observable<T> delay(long delay, TimeUnit unit)`
2. `public final Observable<T> delay(long delay, TimeUnit unit, Scheduler scheduler)`

2.do系列

RxJava中还有一系列的方法可以供我们使用，它们共同的特点是都是以`do`开头，下面我们列举一下这些方法并简要说明一下它们各自的用途：

1. `public final Observable<T> doAfterNext(Consumer<? super T> onAfterNext)`，会在`onNext`方法之后触发；
2. `public final Observable<T> doAfterTerminate(Action onFinally)`，会在Observable终止之后触发；
3. `public final Observable<T> doFinally(Action onFinally)`，当`onComplete`或者`onError`的时候触发；
4. `public final Observable<T> doOnDispose(Action onDispose)`，当被dispose的时候触发；
5. `public final Observable<T> doOnComplete(Action onComplete)`，当complete的时候触发；
6. `public final Observable<T> doOnEach(final Observer<? super T> observer)`，当每个`onNext`调用的时候触发；
7. `public final Observable<T> doOnError(Consumer<? super Throwable> onError)`，当调用`onError`的时候触发；
8. `public final Observable<T> doOnLifecycle(final Consumer<? super Disposable> onSubscribe, final Action onDispose)`
9. `public final Observable<T> doOnNext(Consumer<? super T> onNext)`，，会在`onNext`的时候触发；
9. `public final Observable<T> doOnSubscribe(Consumer<? super Disposable> onSubscribe)`，会在订阅的时候触发；
10. `public final Observable<T> doOnTerminate(final Action onTerminate)`，当终止之前触发。

这些方法可以看作是对操作执行过程的一个监听，当指定的操作被触发的时候会同时触发这些监听方法：

    Observable.range(1, 5)
            .doOnEach(integerNotification -> System.out.println("Each : " + integerNotification.getValue()))
            .doOnComplete(() -> System.out.println("complete"))
            .doFinally(() -> System.out.println("finally"))
            .doAfterNext(i -> System.out.println("after next : " + i))
            .doOnSubscribe(disposable -> System.out.println("subscribe"))
            .doOnTerminate(() -> System.out.println("terminal"))
            .subscribe(i -> System.out.println("subscribe : " + i));

3.subscribeOn & observeOn

`subscribeOn`用于指定Observable自身运行的线程，`observeOn`用于指定发射数据所处的线程，比如Android中的异步任务需要用`observeOn`指定发射数据所在的线程是非主线程，然后执行完毕之后将结果发送给主线程，就需要用`subscribeOn`来指定。比如下面的程序，我们用这两个方法来指定所在的线程：

     Observable.create((ObservableOnSubscribe<Integer>) observableEmitter -> {
        System.out.println(Thread.currentThread());
        observableEmitter.onNext(0);
    }).observeOn(Schedulers.newThread()).subscribeOn(Schedulers.computation())
            .subscribe(integer -> System.out.println(Thread.currentThread()));

最终的输出结果如下所示：

    Thread[RxComputationThreadPool-1,5,main]
    Thread[RxNewThreadScheduler-1,5,main]

4.timeout

用来设置一个超时时间，如果指定的时间之内没有任何数据被发射出来，那么就会执行我们指定的数据项。如下面的程序所示，我们先为设置了一个间隔200毫秒的数字产生器，开始发射数据之前要停顿1秒钟，因为我们设置的超时时间是500毫秒，因而在第500毫秒的时候会执行我们传入的数据项：

    Observable.interval(1000, 200, TimeUnit.MILLISECONDS)
            .timeout(500, TimeUnit.MILLISECONDS, Observable.rangeLong(1, 5))
            .subscribe(System.out::print);
    Thread.sleep(2000);

`timeout`方法有多个重载方法，可以为其指定线程等参数，可以参考源码或者文档了解详情。

#### 2.1.6 错误处理操作符

错误处理操作符主要用来提供给Observable，用来对错误信息做统一的处理，常用的两个是`catch`和`retry`。

1.catch

catch操作会拦截原始的Observable的`onError`通知，将它替换为其他数据项或者数据序列，让产生的Observable能够正常终止或者根本不终止。在RxJava中该操作有3终类型：

1. `onErrorReturn`：这种操作会在onError触发的时候返回一个特殊的项替换错误，并调用观察者的onCompleted方法，而不会将错误传递给观察者；
2. `onErrorResumeNext`：会在onError触发的时候发射备用的数据项给观察者；
3. `onExceptionResumeNext`：如果onError触发的时候onError收到的Throwable不是Exception，它会将错误传递给观察者的onError方法，不会使用备用的Observable。

下面是`onErrorReturn`和`onErrorResumeNext`的程序示例，这里第一段代码会在出现错误的时候输出`666`，而第二段会在出现错误的时候发射数字`12345`：

        Observable.create((ObservableOnSubscribe<Integer>) observableEmitter -> {
            observableEmitter.onError(null);
            observableEmitter.onNext(0);
        }).onErrorReturn(throwable -> 666).subscribe(System.out::print);

        Observable.create((ObservableOnSubscribe<Integer>) observableEmitter -> {
            observableEmitter.onError(null);
            observableEmitter.onNext(0);
        }).onErrorResumeNext(Observable.range(1,5)).subscribe(System.out::print);

2.retry

`retry`使用了一种错误重试机制，它可以在出现错误的时候进行重试，我们可以通过参数指定重试机制的条件。以下面的程序为例，这里我们设置了当出现错误的时候会进行2次重试，因此，第一次的时候出现错误会调用`onNext`，重试2次又会调用2次`onNext`，第二次重试的时候因为重试又出现了错误，因此此时会触发`onError`方法。也就是说，下面这段代码会触发`onNext`3次，触发`onError()`1次：

        Observable.create(((ObservableOnSubscribe<Integer>) emitter -> {
            emitter.onNext(0);
            emitter.onError(new Throwable("Error1"));
            emitter.onError(new Throwable("Error2"));
        })).retry(2).subscribe(i -> System.out.println("onNext : " + i), error -> System.out.print("onError : " + error));

`retry`有几个重载的方法和功能相近的方法，下面是这些方法的定义（选取部分）：

1. `public final Observable<T> retry()`：会进行无限次地重试；
2. `public final Observable<T> retry(BiPredicate<? super Integer, ? super Throwable> predicate)`
3. `public final Observable<T> retry(long times)`：指定重试次数；
4. `public final Observable<T> retry(long times, Predicate<? super Throwable> predicate) `
5. `public final Observable<T> retryUntil(final BooleanSupplier stop)`
6. `public final Observable<T> retryWhen(Function<? super Observable<Throwable>, ? extends ObservableSource<?>> handler)`

#### 2.1.7 条件操作符和布尔操作符

1.all & any

1. `all`用来判断指定的数据项是否全部满足指定的要求，这里的“要求”可以使用一个函数来指定；
2. `any`用来判断指定的Observable是否存在满足指定要求的数据项。

在下面的程序中，我们用该函数来判断指定的数据项是否全部满足大于5的要求，显然是不满足的，因此下面的程序将会输出`false`：

    Observable.range(5, 5).all(i -> i>5).subscribe(System.out::println); // false
    Observable.range(5, 5).any(i -> i>5).subscribe(System.out::println); // true

以下是该方法的定义：

1. `public final Single<Boolean> all(Predicate<? super T> predicate)`
2. `public final Single<Boolean> any(Predicate<? super T> predicate)`

2.contains & isEmpty

这两个方法分别用来判断数据项中是否包含我们指定的数据项，已经判断数据项是否为空：

    Observable.range(5, 5).contains(4).subscribe(System.out::println); // false
    Observable.range(5, 5).isEmpty().subscribe(System.out::println); // false

以下是这两个方法的定义：

1. `public final Single<Boolean> isEmpty()`
2. `public final Single<Boolean> contains(final Object element)`

3.sequenceEqual

`sequenceEqual`用来判断两个Observable发射出的序列是否是相等的。比如下面的方法用来判断两个序列是否相等：

    Observable.sequenceEqual(Observable.range(1,5), Observable.range(1, 5)).subscribe(System.out::println);

4.amb

`amb`作用的两个或多个Observable，但是只会发射最先发射数据的那个Observable的全部数据：

    Observable.amb(Arrays.asList(Observable.range(1, 5), Observable.range(6, 5))).subscribe(System.out::print)

该方法及其功能近似的方法的定义，这里前两个是静态的方法，第二个属于实例方法：

1. `public static <T> Observable<T> amb(Iterable<? extends ObservableSource<? extends T>> sources)`
2. `public static <T> Observable<T> ambArray(ObservableSource<? extends T>... sources)`
3. `public final Observable<T> ambWith(ObservableSource<? extends T> other)`

5.defaultIfEmpty

`defaultIfEmpty`用来当指定的序列为空的时候指定一个用于发射的值。下面的程序中，我们直接调用发射器的`onComplete`方法，因此序列是空的，结果输出一个整数`6`：

    Observable.create((ObservableOnSubscribe<Integer>) Emitter::onComplete).defaultIfEmpty(6).subscribe(System.out::print);

下面是该方法的定义：

1. `public final Observable<T> defaultIfEmpty(T defaultItem)`

#### 2.1.8 转换操作符

1.toList & toSortedList

`toList`和`toSortedList`用于将序列转换成列表，后者相对于前者增加了排序的功能：

    Observable.range(1, 5).toList().subscribe(System.out::println);
    Observable.range(1, 5).toSortedList(Comparator.comparingInt(o -> -o)).subscribe(System.out::println);

下面是它们的定义，它们有多个重载版本，这里选择其中的两个进行说明：

1. `public final Single<List<T>> toList()`
2. `public final Single<List<T>> toSortedList(final Comparator<? super T> comparator)`

注意一下，这里的返回结果是`Single`类型的，不过这并不妨碍我们继续使用链式操作，因为`Single`的方法和`Observable`基本一致。
另外还要注意这里的`Single`中的参数是一个`List<T>`，也就是说，它把整个序列转换成了一个列表对象。因此，上面的两个示例程序的输出是：

    [1, 2, 3, 4, 5]
    [5, 4, 3, 2, 1]

2.toMap & toMultimap

`toMap`用于将发射的数据转换成另一个类型的值，它的转换过程是针对每一个数据项的。以下面的代码为例，它会将原始的序列中的每个数字转换成对应的十六进制。但是，`toMap`转换的结果不一定是按照原始的序列的发射的顺序来的：

    Observable.range(8, 10).toMap(Integer::toHexString).subscribe(System.out::print);

与`toMap`近似的是`toMultimap`方法，它可以将原始序列的每个数据项转换成一个集合类型：

    Observable.range(8, 10).toMultimap(Integer::toHexString).subscribe(System.out::print);

上面的两段程序的输出结果是：

    {11=17, a=10, b=11, c=12, d=13, e=14, f=15, 8=8, 9=9, 10=16}
    {11=[17], a=[10], b=[11], c=[12], d=[13], e=[14], f=[15], 8=[8], 9=[9], 10=[16]}

上面的两个方法的定义是（多个重载，选择部分）：

1. `public final <K> Single<Map<K, T>> toMap(final Function<? super T, ? extends K> keySelector)`
2. `public final <K> Single<Map<K, Collection<T>>> toMultimap(Function<? super T, ? extends K> keySelector)`

3.toFlowable

该方法用于将一个Observable转换成Flowable类型，下面是该方法的定义，显然这个方法使用了策略模式，这里面涉及背压相关的内容，我们后续再详细介绍。

    public final Flowable<T> toFlowable(BackpressureStrategy strategy)

4.to

相比于上面的方法，`to`方法的限制更加得宽泛，你可以将指定的Observable转换成任意你想要的类型（如果你可以做到的话），下面是一个示例代码，用来将指定的整数序列转换成另一个整数类型的Observable，只不过这里的每个数据项都是原来的列表中的数据总数的值：

    Observable.range(1, 5).to(Observable::count).subscribe(System.out::println);

下面是该方法的定义：

`public final <R> R to(Function<? super Observable<T>, R> converter)`

### 2.2 线程控制

之前有提到过RxJava的线程控制是通过`subscribeOn`和`observeOn`两个方法来完成的。
这里我们梳理一下RxJava提供的几种线程调度器以及RxAndroid为Android提供的调度器的使用场景和区别等。

1. `Schedulers.io()`：代表适用于io操作的调度器，增长或缩减来自适应的线程池，通常用于网络、读写文件等io密集型的操作。重点需要注意的是线程池是无限制的，大量的I/O调度操作将创建许多个线程并占用内存。
2. `Schedulers.computation()`：计算工作默认的调度器，代表CPU计算密集型的操作，与I/O操作无关。它也是许多RxJava方法，比如`buffer()`,`debounce()`,`delay()`,`interval()`,`sample()`,`skip()`，的默认调度器。
3. `Schedulers.newThread()`：代表一个常规的新线程。
4. `Schedulers.immediate()`：这个调度器允许你立即在当前线程执行你指定的工作。它是`timeout()`,`timeInterval()`以及`timestamp()`方法默认的调度器。
5. `Schedulers.trampoline()`：当我们想在当前线程执行一个任务时，并不是立即，我们可以用`trampoline()`将它入队。这个调度器将会处理它的队列并且按序运行队列中每一个任务。它是`repeat()`和`retry()`方法默认的调度器。

以及RxAndroid提供的线程调度器：

`AndroidSchedulers.mainThread()`用来指代Android的主线程

### 2.3 总结

上面的这些操作也基本适用于`Flowable`、`Single`、`Completable`和`Maybe`。

我们花费了很多的时间和精力来梳理了这些方法，按照上面的内容，使用RxJava实现一些基本的或者高级的操作都不是什么问题。

但是，Observable更适用于处理一些数据规模较小的问题，当数据规模比较多的时候可能会出现`MissingBackpressureException`异常。
因此，我们还需要了解背压和`Flowable`的相关内容才能更好地理解和应用RxJava.
