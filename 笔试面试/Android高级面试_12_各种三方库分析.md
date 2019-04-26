# Android 高级面试：三方库源码分析

## 1、ARouter

既然使用的时候需要 AnnotationProcessor，那么说明它是基于注解处理的，也就是编译期间根据注解动态生成代码，当然生成的代码要符合既定的规则，然后根据规则使用反射加载到内存中。ARouter 在编译期间生成路由到指定的类的对应的关系，当然包括类的类型信息：

```java
public class ARouter$$Group$$app implements IRouteGroup {
  @Override
  public void loadInto(Map<String, RouteMeta> atlas) {
    atlas.put("/app/intro", RouteMeta.build(RouteType.ACTIVITY, AppIntroActivity.class, "/app/intro", "app", null, -1, -2147483648));
  }
}
```

然后当调用 `navigation()` 方法的时候，根据路由字符串从映射关系中取出对应的类。然后根据类的类型选择指定类型的启动方式。当然，本质上还是采用 Intent 来进行启动的。

在模块化开发的时候这些自动生成的类被分散到各个模块里，这样各个模块之前的页面通过路由相互引用而不是类本身。因为如果是类本身进行引用的话，会因为类的引用找不到而导致编译失败。使用字符串映射就可以将各个模块编译成独立的 APK。而组件开发完毕之后，统一打包之后，就将所有的映射关系统一放进一个 APK 里，然后就可以在当前包中根据路由找到类并使用。

ARouter 的另一个功能是实现变量的自动注入，这个功能的实现原理和 ButterKnife 的实现相似。早期的版本是基于 Hook 来实现的，但是后来被废弃了，转而采用了使用注解的方式。它本质上依赖于我们在类内部调用 `inject()` 方法。当我们调用这个方法的时候，ARouter 会根据类名和指定的字符串拼接起来找到生成的类，这个生成的类会在指定的方法中为我们 Activity 或者 Fragment 的字段进行赋值。因为我们不会为了注入的变量提供 setter 方法，所以通常这类注入至少要保证该变量是包级别的访问权限。这也是为什么很多注入框架的要注入的变量都要求是包级别访问权限的原因。

## 2、图片加载框架

**问题：Glide 源码？**   
**问题：Glide 使用什么缓存？**    
**问题：Glide 内存缓存如何控制大小？**   
**问题：图片框架实现原理？**   

1. `Glide 的请求的生命周期的控制`：它使用没有 UI 的 Fragment 来管理 Glide 的生命周期。RxPermission 和 LiveData 的早期的版本都是使用这种方式来解决的。把一个回调设置到 Fragment 中，然后在它的生命周期方法中回调这个回调接口的方法。

2. 当我们第一次使用 Glide 发起请求的时候会实例化一个`单例的 Glide`，在初始化 Glide 的过程中会调用我们的自定义 `GlideModule`. GlideModule 的方法提供了构建者供我们使用，拿到了这个构建者之后，我们就可以使用它的方法进行自定义配置。GlideModule 的方法还提供了用来向 Glide 中注册自定义类型的文件的加载方式的 Factory。它们会以映射的方式存储到 Glide 中。当加载自定义类型图片的时候，会根据映射关系，先拿到 Factory，然后从中获取加载该图片的 Loader，并使用它来加载数据。对于自定义的类型，如何对其进行缓存，缓存的 key 如何设置（这影响缓存的命中），都可以通过 GlideModule 来完成。

3. 图片加载的核心流程位于 `DecodeJob 和 EngineJob`, 它们之间的关系是 DecodeJob 用来完成图片的加载、解密等工作；EngineJob 内部维护了一个线程池用来对 DecodeJob 进行调度。另外，对于请求信息都被包装到 Request 对象中，请求的过程也发生在 Request 对象中。用来显式图片的对象则需要继承 Target. 图片加载的逻辑位于 Request，在 SingleRequest 中使用了状态模式，因为图片加载可能中断、恢复和完成等，所以可以根据当前的状态做相应处理。图片是从缓存中进行加载还是从原始的数据源中进行加载也是在状态模式中进行控制的。此外，还包括从原始的数据源加载完毕之后，缓存到磁盘上面的过程，也是在该状态模式中进行处理的。

4. 在默认配置中，当 Glide 进行图片加载的时候会先从缓存当中进行获取。Glide 采用了多种缓存，包括两种内存缓存和一种磁盘缓存。首先，图片会先从弱引用的内存缓存中获取数据。弱引用的缓存会因为内存不足而回收；当若引用中获取不到的时候，再从 LruCache 的内存缓存中获取数据。当内存缓存中获取不到的时候，会尝试从磁盘缓存中获取数据。它会使用图片的参数构建一个 Key 用来从磁盘上面读取图片。这里去从磁盘上面读取数据的时候也会使用与文件类型映射的 Loader 来从磁盘上面加载数据。当磁盘上面找不到缓存的时候，则回到之前的状态模式，从原始数据源中获取数据。当获取到数据之后，再使用磁盘缓存将其缓存到磁盘上面。
5. 当 Glide 使用 Loader 加载数据之后会得到一个 InputStream，然后再经过层层回调使用 BitmapFactory 的 `decode()` 方法从该流中加载 Bitmap. 然后就是对其进行变换，并将其显式到控件上面的过程。

Glide 的缓存控制，可以在自定义 GlideModule 的时候，通过构建者的方法对其进行配置。然后，可以在每次请求的时候，使用请求的构建者方法对本次请求的缓存策略进行配置。

**问题：对 Bitmap 对象的了解？**      
**问题：Bitmap 使用时候注意什么？**   

Bitmap 占用内存大小的计算，首先可以通过 `getByteCount()` 和 `getAllocationByteCount()` 获取到占用内存的大小。Bitmap 占用内存的大小可以通过 `像素数量*每个像素占用的字节` 得到。每个像素占用的字节可以通过枚举 Config 指定，常用的有下面四种：

1. `ARGB_8888`：每个像素占`四个字节`，A、R、G、B 分量各占 8 位，是 Android 的默认设置；
2. `RGB_565`：每个像素占`两个字节`，R 分量占 5 位，G 分量占 6 位，B 分量占 5 位；
3. `ARGB_4444`：每个像素占`两个字节`，A、R、G、B 分量各占 4 位，成像效果比较差；
5. `Alpha_8`: 只保存透明度，共 8 位，`1 字节`；

所以问题在于如何得到像素的数量，`像素总数=宽度像素*高度像素`。这里的宽度和高度的像素又根据资源的位置分成两种情况，当资源位于网络或者磁盘上面的时候，通过 `decode()` 加载到内存中的时候，占用内存大小只与图片本身有关，与设备屏幕密度无关，此时 `占用内存=实际显示的宽 * 实际显示的高 * Bitmap.Config`。但如果资源是位于 drawable 文件夹下面，则会根据资源所处的位置和屏幕密度发生改变，此时计算公式如下：

```java
// 这里：
// 1. width 和 height 是原素材大小； 
// 2. targetDensity 是设备像素密度； 
// 3. density 是素材所在 drawable 文件夹大小；

int scaledWidth = (int) (width * targetDensity / density + 0.5f) 
int scaledHeight = (int) (height * targetDensity / density + 0.5f) 
int size = scaledWidth * scaledHeight * Bitmap.Config

// 屏幕的像素密度可以通过下面的方法获取
DisplayMetrics metric = new DisplayMetrics();
int targetDensity = metric.densityDpi;
// 而 density 的对应关系是：mdpi->160dp (默认), hdpi->240dp, xhdpi->320dp, xxhdpi->480dp, xxxhdpi->640dp
```

所以，同一种图片方的文件夹的像素密度越大，则占用内存越小。另外，对于 jpg 和 png，同一尺寸的图片被加载到内存之后占用的内存是相同的，但是在磁盘上面占用的内存是不同的。因此，为减小 apk 体积，建议使用 jpg.

**问题：Bitmap recycler 相关**    

从 3.0 开始，Bitmap 像素数据和 Bitmap 对象一起存放在 Dalvik 堆中，而在 3.0 之前，Bitmap 像素数据存放在 Native 内存中。所以，在 3.0 之前，Bitmap 像素数据在 Nativie 内存的释放是不确定的，容易内存溢出而 Crash，官方强烈建议调用 `recycle()`（当然是在确定不需要的时候）；而在 3.0 之后，则无此要求。（*参考：[Managing Bitmap Memory](https://developer.android.com/topic/performance/graphics/manage-memory)*）

**问题：图片加载原理？**    
**问题：图片压缩原理？**

Android 平台上面图片压缩有两种方式：`质量压缩`和`尺寸压缩`，其中尺寸压缩又分为`邻近采样`和`双线性采样`两种方式。在把图片从磁盘上面加载到内存中的时候需要使用 BitmapFactory 的 `decode()` 方法加载指定类型的资源，得到一个 Bitmap. 在调用 `decode()` 方法的时候需要使用 `BitmapFactory.Option` 指定采样的比例。为了计算采样率，你需要在真正地 `deocde()` 资源之前得到图片的尺寸。可以通过设置 `decode()` 方法的 `Options` 的 `inJustDecodeBounds` 字段为 false 来实现。真正加载的时候再设置其为 true. 这样进行采样的时候就进行了第一次压缩，叫做邻近采样。然后，对得到的 Bitmap 实例调用 `compress()` 方法，并指定一个图片的质量，通常是在 0~100 之间。这样就可以对图片的质量进行压缩，质量的值越大，图片质量越高，图片也越大。最后是双线性压缩，我们可以调用 Bitmap 的 `createScaledBitmap()` 或者调用 `createBitmap()` 并传入一个 Matrix 来实现。双线性压缩的好处是可以得到一个图片的固定的大小。

那么，在实际的使用过程中，我们可以根据我们的应用场景选择适合的压缩方式。比如，在我们的场景中，先使用尺寸压缩得到 Bitmap. 因为 Bitmap 太大的话，加载到内存中可能会发生 OOM. 然后，可以再使用双线性压缩把图片的尺寸压缩到固定的大小。最后就是使用质量压缩，通过降低图片质量来把降低图片的大小。

Android 中加载图片使用的就是 BitmapFactory 的一系列 `decode()` 方法，包括 Glide 内部也是从各种流中获取到一个 Bitmap. 这些 `decode()` 方法会调用 Native 的方法。Native 方法中的实现引用了 Skia 来实现。Skia 是谷歌的一个 2D 图像库，用 C++ 实现。它提供了适用于多种应用平台的公共 API. 并作为 Chrome, Android 等的图片处理引擎。

Skia 本身提供了基本的画图和编解码功能，它同时还挂载了其他第三方编解码库，例如：libpng.so、libjpeg.so、libgif.so. 指定类型的图片将交给指定类型的编码库来完成。

Skia 的资料：[官方网址](https://skia.org/)、[Github 地址](https://github.com/google/skia) 以及[在 Android 源码中的位置](https://android.googlesource.com/platform/external/skia/)。

**问题：大图加载**    
**问题：图片加载库相关，Bitmap 如何处理大图，如一张 30M 的大图，如何预防 OOM**    

使用 BitmapRegionDecoder 来部分加载图片。加载图片的时候调用下面的方法即可，

```java
public Bitmap decodeRegion(Rect rect, BitmapFactory.Options options)
```

在控件的 `onDraw()` 方法中进行绘制，然后使用 GestureDetector 来检查手势，当移动图片的时候调用 `invalid()` 方法进行重绘即可。

**问题：几种图片加载框架的对比**

1. Glide 比 Picasso `包体积`更大，方法数更多，使用的时候需要开启混淆。
2. Glide 相比 Picasso 的一大优势是它可以和 Activity 以及 Fragment 的`生命周期`相互协作。
3. Picasso 将图片下载后会`不经压缩直接将图片整个缓存到磁盘`中，当需要用到图片时，它会直接返回这张完整大小的图片，并在运行时根据 ImageView 的大小作适配。而 Glide 会综合要显式的图片的尺寸和变换等信息，构建一个缓存的 Key，然后将变换之后的图片缓存。此外，它还提供了可配置的缓存策略。
4. 在加载同样配置的图片时，`Glide 内存占用`更少，这从前面的讨论中其实可以猜测到了，Picasso 是将完整大小的图片加载进内存，然后依赖 GPU 来根据 ImageView 的大小来适配并渲染图片，而 Glide 是针对每个 ImageView 适配图片大小后再存储到磁盘的，这样加载进内存的是压缩过的图片，内存占用自然就比 Picasso 要少。
5. `图片加载的耗时`：Picasso 相比 Glide 要快很多。可能的原因是 Picasso 下载完图片后直接将整个图片加载进内存，而 Glide 还需要针对每个 ImageView 的大小来适配压缩下载到的图片，这个过程需要耗费一定的时间。
6. Glide 独有的特性：对 `GIF 动画`的支持，`缩略图`的支持。
7. `缓存格式`：默认使用 ARGB_8888 格式缓存图片, 缓存体积大；Glide 默认使用 RGB565.

Fresco - Facebook：

1. 图片存储在安卓系统的`匿名共享内`存, 而不是`虚拟机的堆内存`中, 图片的中间缓冲数据也存放在本地堆内存, 所以, 应用程序有更多的内存使用，不会因为图片加载而导致 OOM, 同时也减少垃圾回收器频繁调用回收 Bitmap 导致的界面卡顿，性能更高。
2. 图片可以以任意的中心点显示在 ImageView, 而不仅仅是图片的中心。
3. `JPEG 图片改变大小也是在 native 进行的`, 不是在虚拟机的堆内存, 同样减少 OOM；
4. 很好的支持 GIF 图片的显示

缺点: 1). `框架较大`, 影响Apk体积；2). `使用较繁琐`。

ImageLoader：

1. 支持下载进度监听
2. 可以在 View 滚动中暂停图片加载 
3. 默认实现多种内存缓存算法这几个图片缓存都可以配置缓存算法，不过 ImageLoader 默认实现了较多缓存算法，如 Size 最大先删除、使用最少先删除、最近最少使用、先进先删除、时间最长先删除等。
4. 支持本地缓存文件名规则定义

缺点: 缺点在于`不支持 GIF` 图片加载,  `缓存机制`没有和 http 的缓存很好的结合, 完全是自己的一套缓存机制。

## 3、EventBus

**问题：EventBus 作用，实现方式，代替 EventBus 的方式**
**问题：EventBus 实现原理？**

EventBus 事件发布-订阅总线，它简化了应用程序内各个组件之间进行通信的复杂度，尤其是碎片之间进行通信的问题，可以避免由于使用广播通信而带来的诸多不便。

```java
// 收消息
public class EventBusActivity1 extends CommonActivity<ActivityEventBus1Binding> {

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGetMessage(MessageWrap message) {
        getBinding().tvMessage.setText(message.message);
    }
}

// 发消息：
EventBus.getDefault().post(MessageWrap.getInstance(msg));
```

当调用 `register()` 方法的时候会先获取到调用这个方法的类，然后根据类名，从缓存当中获取之前解析过的信息，否则会对类进行解析。这里解析的内容主要是类的方法，它会将使用了订阅注解的方法信息、注解内容等封装成一个类。这里的注册类到方法列表的映射缓存是通过 ConcurrentHashMap 类实现的。

拿到了方法的列表之后，就通过循环对每个方法信息调用 `subscribe()` 执行订阅的逻辑。订阅的时候主要做了几件事情，第一它通过 CopyOnWriteArrayList 维护事件类型到观察者的订阅关系，根据事件的优先级与观察者的优先级，决定哪个观察者应该进入到这个事件的对应的观察者列表中。然后，它要构建观察者到观察者的事件类型的映射关系。最后，它根据订阅的事件是否存在于粘性事件的映射表中，来决定是否应该触发粘性事件。（刚订阅的时候，需要把粘性事件的结果通知给订阅的对象。）

当 post() 消息的时候，它会先获取当前线程对应的状态信息，该状态信息中维护了一个事件队列，当 post() 信息的时候，不断循环从这个队列中取出消息并继续分发。它会先从上述的事件-观察者映射列表中取出所有观察者，然后继续分发事件。在分发事件的时候会根据事件的线程的状态，分成几种情况来进行处理。如果事件指定的线程与当前线程相同，那么直接触发方法即可（反射），否则如果要到主线程当中触发，就会在底层使用 Handler 将消息发送到主线程进行处理；如果是异步触发，则把反射的操作放在线程池当中执行。

## 4、RxJava

**问题：RxJava 的作用，优缺点**    
**问题：RxJava 的作用，与平时使用的异步操作来比，优势**    
**问题：RxJava 简介及其源码解读？**

虽然 RxJava 的功能非常强大，但是其核心的实现却仅仅依赖两个设计模式，一个是观察者模式，另一个是装饰器模式。它采用了类似于 Java 的流的设计，每个装饰器负责自己一种任务，这复合单一责任原则；各个装饰器之间相互协作，来完成复杂的功能。从上面的源码分析过程中我们也可以看出，RxJava 的缺点也是非常明显的，大量的自定义类，在完成一个功能的时候各装饰器之间不断包装，导致调用的栈非常长。至于线程的切换，它依赖于自己的装饰器模式，因为一个装饰器可以决定其上游的 Observable 在哪些线程当中执行；两个装饰器处于不同的线程的时候，从一个线程中执行完毕自然进入到另一个线程中执行就完成了线程切换的过程。（参考：[RxJava 系列-4：RxJava 源码分析](https://juejin.im/post/5c701480e51d457f14363fd5)。）

优势：实现线程切换更加容易，没有太多的回调    
劣势：调用栈太长

**问题：RxJava zip 操作**

```java
Observable<String> a = // ... A 请求
Observable<Integer> b =  // ... B 请求
Observable.zip(a, b, new BiFunction<String, Integer, Object>(){
    @Override
    public Object apply(@NonNull String s, @NonNull Integer integer) throws Exception {
        // 拿到了 A 请求和 B 请求的第 n 次执行的结果
        return new Object();
    }
}).subscribe();
```

A 和 B 会并行在各自的子线程当中, 并且会合并到 `apply()` 方法中。它能保证 B 操作在 A 操作之前执行。我们可以使用这种方式来实现线程的控制。即当一个任务完成之后才执行另一个任务，同时它们的任务的结果可以被合并。那么合并的规则是什么呢？如果 A 和 B 多次发送结果（也就是多次调用 `onNext()` 方法），此时，A 和 B 发送的结果会按照先后的顺序配对，并回调上述的 `BiFunction` 函数。

**问题：RxJava FlatMap 操作**

`flatMap()` 也是一种 `map()`，只是不同的是：假如传入的是一个列表，那么 `map()` 对列表的每一个元素进行变换，然后变换的元素又构成了一个集合。而 `flatMap()` 也可以对列表的每个元素进行变换，只是它变换之后的结果强制是 Observable 类型的，并且如果这些返回的 Observable 又都由列表构成，那么这些映射之后的列表将会构成一个新的列表，交给最终的 Observable. `flatMap()` 与 `contactMap()` 不同的地方是，前者会按照原始列表的顺序拼接返回的列表，而后者不会。

```java
        Observable.range(1, 5).flatMap(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer integer) throws Exception {
                return Observable.range(integer + 100, 2);
            }
        }).subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                System.out.print(integer + " ");
            }
        });
```

## 5、数据库

**问题：数据库框架对比和源码分析？**    
**问题：SQLite 数据库升级，数据迁移问题？**    
**问题：SQLite 升级，增加字段的语句**    
**问题：数据库数据迁移问题**

数据库框架：Room 出现之前使用最多的是 OrmLite 和 GreenDAO. ORMLite 和 JavaWeb 框架中的 Hibernate 相似，都是使用注解的方式来标注数据库中的表、字段、关联关系的，这也是 ORMLite 的工作原理：ORMLite 是基于反射机制工作的，然而这也成为了 ORMLite 的一个非常致命的缺点，性能不好。因此，如果是对想能要求不高的项目，我们可以考虑使用 ORMLite，而如果项目对性能要求较高，我们可以考虑使用 GreenDAO. 

个人之前也搭建过简单的数据库框架，只是在 Android 的基础的 SQLite 类基础之上做了一层封装。我也是使用注解，但是对注解的解析只在第一次创建表的时候使用。对 ContentValue 和 Cursur 操作的时候使用的是它们的 setter 和 getter 进行赋值。顶层对基础的查询等操作做了封装，各个子类只需要实现模板的方法，无需写 SQL 就可以实现数据库的基本操作。但是 setter 和 getter 的过程比较繁琐，而且这种重复性的工作是应该交给程序来解决的。这个问题可以使用注解处理进行优化，在编译器期间按规则把这些逻辑写好。（没做的主要原因是出现了 Room，没必要重复造轮子了。）

GreenDao 相较于 ORMLite 等其他数据库框架有以下优势：更精简；性能最大化；内存开销最小化；易于使用的 APIs；对 Android 进行高度优化。Android 中使用比较常见的注解处理器是 APT，但是 GreenDao 使用的是 JDT。代码生成框架使用的是 FreeMarker，对应的还有一种生成代码的方式叫 javapoet 的。GreenDAO 也是通过在编译期间生成代码来完成数据库的字段到对象的属性的映射。

**问题：数据库升级**

```java
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
            case 2:
                db.execSQL("ALTER TABLE gt_note ADD COLUMN " + NoteSchema.PREVIEW_IMAGE + " TEXT");
                db.execSQL("ALTER TABLE gt_note ADD COLUMN " + NoteSchema.NOTE_TYPE + " INTEGER");
            case 4:
                db.execSQL("ALTER TABLE gt_note ADD COLUMN " + NoteSchema.PREVIEW_CONTENT + " TEXT");
                break;
            case 5:
                // 判断指定的两个列是否存在，如果不存在的话就创建列
                Cursor cursor = null ;
                try{
                    cursor = db.rawQuery( "SELECT * FROM " + tableName + " LIMIT 0 ", null );
                    boolean isExist = cursor != null && cursor.getColumnIndex(NoteSchema.PREVIEW_IMAGE) != -1 ;
                    if (!isExist) {
                        db.execSQL("ALTER TABLE gt_note ADD COLUMN " + NoteSchema.PREVIEW_IMAGE + " TEXT");
                        db.execSQL("ALTER TABLE gt_note ADD COLUMN " + NoteSchema.NOTE_TYPE + " INTEGER");
                    }
                } finally{
                    if(null != cursor && !cursor.isClosed()){
                        closeCursor(cursor);
                    }
                }
                break;
        }
    }
```

**问题：打开 db 文件**，

## 其他

**问题：用到的一些开源框架，介绍一个看过源码的，内部实现过程**    
**问题：ButterKnife 实现原理？**

ButterKnife 基于注解处理，在运行时根据 Activity 的名称生成对应的注射类。可以在 Activity 名称后面增加 `$Injector` 的方式来实现。然后会根据这个类中使用的注解绑定的控件和事件，在编译期间赋值的代码。当我们调用 `bind()` 方法的时候，根据当前的类名寻找注射器。然后调用它的 `inject()` 方法，在这个生成的方法中去执行 `findViewById()` 和 `setOnClickListener()` 等工作。

