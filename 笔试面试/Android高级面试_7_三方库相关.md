# Android 高级面试-7：三方库相关

*同种功能的类库之间对比，同时分析各种库的设计的优缺点*

## 1、网络框架

- HttpUrlConnection, HttpClient, Volley & OkHttp 的区别？

```java
    URL url = new URL("http://www.baidu.com"); // 创建 URL
    HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 获取 HttpURLConnection
    connection.setRequestMethod("GET"); // 设置请求参数
    connection.setConnectTimeout(5 * 1000);
    connection.connect();
    InputStream inputStream = connection.getInputStream(); // 打开输入流
    byte[] data = new byte[1024];
    StringBuilder sb = new StringBuilder();
    while (inputStream.read(data) != -1) { // 循环读取
        String s = new String(data, Charset.forName("utf-8"));
        sb.append(s);
    }
    message = sb.toString();
    inputStream.close(); // 关闭流
    connection.disconnect(); // 关闭连接
```

从功能上对比，HttpClient 库要丰富很多，提供了很多工具，封装了 http 的请求头，参数，内容体，响应，还有一些高级功能，代理、COOKIE、鉴权、压缩、连接池的处理。HttpClient 高级功能代码写起来比较复杂，对开发人员的要求会高一些，而 HttpURLConnection 对大部分工作进行了包装，屏蔽了不需要的细节，适合开发人员直接调用。另外，HttpURLConnection 在 2.3 版本增加了一些 HTTPS 方面的改进，4.0 版本增加一些响应的缓存。

HttpURLConnect 是一个通用的、适合大多数应用的轻量级组件。这个类起步比较晚，很容易在主要 API 上做稳步的改善。但是 HttpURLConnection 在Android 2.2 及以下版本上存在一些令人厌烦的 bug，尤其是在读取 InputStream 时调用 close() 方法，可能会导致连接池失效。Android 2.3 及以上版本建议选用 HttpURLConnection，2.2 及以下版本建议选用 HttpClient。新的应用都建议使用 HttpURLConnection。

HttpClient 的 API 数量过多，使得我们很难在不破坏兼容性的情况下对它进行升级和扩展，所以，目前 Android 团队在提升和优化HttpClient方面的工作态度并不积极。

OkHttp 是一个现代，快速，高效的 Http client，支持 HTTP/2 以及 SPDY. Android 4.4 的源码中可以看到 HttpURLConnection 已经替换成 OkHttp 实现了。OkHttp 处理了很多网络疑难杂症：会从很多常用的连接问题中自动恢复。OkHttp 还处理了代理服务器问题和 SSL 握手失败问题。

Volley 非常适合进行数据量不大，但通信频繁的网络操作；内部分装了异步线程；支持 Get，Post 网络请求和图片下载；可直接在主线程调用服务端并处理返回结果。缺点是：对大文件下载 Volley 的表现非常糟糕，并且只支持 http 请求。

Volley 封装了访问网络的一些操作，但是底层在 Android 2.3 及以上版本，使用的是 HttpURLConnection，而在 Android 2.2 及以下版本，使用的是 HttpClient. 

- OkHttp 源码？
- 网络请求缓存处理，OkHttp 如何处理网络缓存的

首先从整体的架构上面看，OkHttp 是基于`责任链设计模式`设计的，责任链的每一个链叫做一个拦截器，OkHttp 的请求是依次通过`重试、桥接、缓存、连接和访问服务器`五个责任链，分别用来进行错误重连，从缓存中获取请求结果，建立服务器连接以及最终从服务器中拿到请求结果。其中当从缓存中拿到了结果的时候，可以直接返回缓存中的结果而无需从服务器中获取请求。除此之外，我们还可以自定义自己的拦截器。

重试阶段主要是根据请求的响应重试次数等，来决定是否应该继续应该重新发送请求。如果服务器返回了重定向信息，那么它还要使用新的地址重新获取数据。

OkHttp 的缓存最终是使用的 DiskLruCache 将请求的请求和响应信息存储到磁盘上。当进入到缓存拦截器的时候，首先会先从缓存当中获取请求的请求信息和响应信息。它会从响应信息的头部获取本次请求的缓存信息，比如过期时间之类的，然后判断该响应是否可用。如果可用，或者处于未连网状态等，则将其返回。否则，再从网络当中获取请求的结果。当拿到了请求的结果之后，还会再次回到缓存拦截器。缓存拦截器在拿到了响应之后，再根据响应和请求的信息决定是否将其持久化到磁盘上面。

OkHttp 的网络访问并没有直接使用 HttpUrlConnection 或者 HttpClient 而是直接使用 Socket 建立网络连接，对于流的读写，它使用了第三方的库 okio。在拿到一个请求的时候，OkHttp 首先会到连接池中寻找可以复用的连接。这里的连接池是使用双端队列维护的一个列表。当从连接池中获取到一个连接之后就使用它来进行网络访问。

- Retrofit 源码？

Retrofit 的实现主要是通过动态代理，核心的地方就是调用 `Proxy.newProxyInstance()` 方法，来获取一个代理对象，然后通过传入的 `InvocationHandler` 对接口的方法进行解析，获取方法的注解等信息。

```java
private static <T> T getProxy(final Class<T> service) {
    InvocationHandler h = (proxy, method, args) -> {
        String json = "{}";
        if (method.getName().equals("getAInfo")) {
            json = "{A请求的结果}";
        } else if (method.getName().equals("getBInfo")) {
            json = "{B请求的结果}";
        }
        return json;
    };
    return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service}, h);
}
```

方法所包含的接口信息会被封装成一个类，并且 Retrofit 会对其进行缓存。

Retrofit 的另外两个设计比较好的地方，一个是提供了 Adpater 用来将请求包装成我们指定的类型。比如，它可以将请求包装成一个 RxJava 的 Observable 类型。当然，要转换成哪种类型需要使用的人自己提供一个 Adapter 的实现。然后以 RxJava 为例，当代理类的方法被调用的时候会返回一个 Observable. 然后，当我们对 Observable 进行订阅的时候将会调用 `subscribeActual()`，在该方法中使用 OkHttp 从网络中获取数据。

当拿到了数据之后就是如何将数据转换成我们期望的类型。这里 Retrofit 也将其解耦了出来。Retrofit 提供了 `Converter` 用作 OkHttp 的响应到我们期望类型的转换器。我们可以通过自己定义来实现自己的转换器，并选择自己满意的 Json 等转换框架。

- Volley 实现原理？

```java
    RequestQueue queue = Volley.newRequestQueue(this);
    // 针对不同的请求类型，Volley 提供了不同的 Request
    queue.add(new StringRequest(Request.Method.POST, "URL", new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {

        }
    }, new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {

        }
    }));
```

Volley 封装了访问网络的一些操作，但是底层在 Android 2.3 及以上版本，使用的是 HttpURLConnection，而在 Android 2.2 及以下版本，使用的是 HttpClient. 当创建一个 RequestQueue 的时候会同时创建 4 条线程用于从网络中请求数据，一条缓存线程用来从缓存中获取数据。因此不适用于数据量大、通讯频繁的网络操作，因为会占用网络请求的访问线程。

当调用 `add()` 方法的时候，会先判断是否可以使用缓存，如果可以则将其添加到缓存队列中进行处理。否则将其添加到网络请求队列中，用来从网络中获取数据。在缓存的分发器中，会开启一个无限的循环不断进行工作，它会先从阻塞队列中获取一个请求，然后判断请求是否可用，如果可用的话就将其返回了，否则将请求添加到网络请求队列中进行处理。

网络请求队列与之类似，它也是在 `run()` 方法中启动一个无限循环，然后使用阻塞队列获取请求，拿到请求之后来从网络中获取响应结果。

*Volley 的源码逻辑比较清晰，详情请参考这篇博文，就不专门分析了：[Volley使用及其原理解析](https://www.jianshu.com/p/fbbf2b1dfa46)*

## 2、图片加载框架

- Glide 源码？
- Glide 使用什么缓存？
- Glide 内存缓存如何控制大小？
- 图片框架实现原理？

1. Glide 的请求的生命周期的控制：它使用没有 UI 的 Fragment 来管理 Glide 的生命周期。RxPermission 和 LiveData 的早期的版本都是使用这种方式来解决的。我们可以把一个回调设置到 Fragment 中，然后在它的生命周期方法中回调这个回调接口的方法。
2. 当我们第一次使用 Glide 发起请求的时候会实例化一个单例的 Glide，在初始化 Glide 的过程中会调用我们的自定义 GlideModule. GlideModule 提供了两个方法可以供我们对 Glide 进行个性化地配置，其中一个提供了构建者，那么拿到了这个构建者之后，我们就可以使用它的方法进行各种配置。另外一个是用来向 Glide 中注册自定义类型的文件的加载方式的 Factory。它们会以一个映射的方式存储到 Glide 中。当加载自定义类型图片的时候，会根据该映射关系，先拿到 Factory，然后从中获取加载该图片的 Loader，并使用它来加载数据。对于自定义的类型，如何对其进行缓存，缓存的 key 如何设置（这影响缓存的命中），都可以通过 GlideModule 来完成。
3. 图片加载的核心流程位于 DecodeJob 和 EngineJob, 它们之间的关系是 DecodeJob 用来完成图片的加载、解密等工作；EngineJob 内部维护了一个线程池用来对 DecodeJob 进行调度。另外，对于请求信息都被包装到 Request 对象中，请求的过程也发生在 Request 对象中。用来显式图片的对象则需要继承 Target. 图片加载的逻辑位于 Request，在 SingleRequest 中使用了状态模式，因为图片加载可能中断、恢复和完成等，所以可以根据当前的状态做相应处理。图片是从缓存中进行加载还是从原始的数据源中进行加载也是在状态模式中进行控制的。此外，还包括从原始的数据源加载完毕之后，缓存到磁盘上面的过程，也是在该状态模式中进行处理的。
4. 在默认配置中，当 Glide 进行图片加载的时候会先从缓存当中进行获取。Glide 采用了多种缓存，包括两种内存缓存和一种磁盘缓存。首先，图片会先从弱引用的内存缓存中获取数据。弱引用的缓存会因为内存不足而回收；当若引用中获取不到的时候，再从 LruCache 的内存缓存中获取数据。当内存缓存中获取不到的时候，会尝试从磁盘缓存中获取数据。它会使用图片的参数构建一个 Key 用来从磁盘上面读取图片。这里去从磁盘上面读取数据的时候也会使用与文件类型映射的 Loader 来从磁盘上面加载数据。当磁盘上面找不到缓存的时候，则回到之前的状态模式，从原始数据源中获取数据。当获取到数据之后，再使用磁盘缓存将其缓存到磁盘上面。
5. 当 Glide 使用 Loader 加载数据之后会得到一个 InputStream，然后再经过层层回调使用 BitmapFactory 的 `decode()` 方法从该流中加载 Bitmap. 然后就是对其进行变换，并将其显式到控件上面的过程。

Glide 的缓存控制，可以在自定义 GlideModule 的时候，通过构建者的方法对其进行配置。然后，可以在每次请求的时候，使用请求的构建者方法对本次请求的缓存策略进行配置。

- 对 Bitmap 对象的了解？
- Bitmap recycler 相关
- Bitmap 使用时候注意什么？

Bitmap 占用内存大小的计算，首先可以通过 `getByteCount()` 和 `getAllocationByteCount()` 获取到占用内存的大小。Bitmap 占用内存的大小可以通过 `像素数量*每个像素占用的字节` 得到。每个像素占用的字节可以通过枚举 Config 指定，常用的有下面四种：

1. ARGB_8888：每个像素占`四个字节`，A、R、G、B 分量各占 8 位，是 Android 的默认设置；
2. RGB_565：每个像素占`两个字节`，R 分量占 5 位，G 分量占 6 位，B 分量占 5 位；
3. ARGB_4444：每个像素占`两个字节`，A、R、G、B 分量各占 4 位，成像效果比较差；
5. Alpha_8: 只保存透明度，共 8 位，`1 字节`；

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

**关于 recycler() 方法**：从 3.0 开始，Bitmap 像素数据和 Bitmap 对象一起存放在 Dalvik 堆中，而在 3.0 之前，Bitmap 像素数据存放在 Native 内存中。所以，在 3.0 之前，Bitmap 像素数据在 Nativie 内存的释放是不确定的，容易内存溢出而 Crash，官方强烈建议调用 `recycle()`（当然是在确定不需要的时候）；而在 3.0 之后，则无此要求。（*参考：[Managing Bitmap Memory](https://developer.android.com/topic/performance/graphics/manage-memory)*）

- 图片加载原理？
- 图片压缩原理？

Android 平台上面图片压缩有两种方式：`质量压缩`和`尺寸压缩`，其中尺寸压缩又分为`邻近采样`和`双线性采样`两种方式。在把图片从磁盘上面加载到内存中的时候需要使用 BitmapFactory 的 `decode()` 方法加载指定类型的资源，得到一个 Bitmap. 在调用 `decode()` 方法的时候需要使用 `BitmapFactory.Option` 指定采样的比例。为了计算采样率，你需要在真正地 `deocde()` 资源之前得到图片的尺寸。可以通过设置 `decode()` 方法的 `Options` 的 `inJustDecodeBounds` 字段为 false 来实现。真正加载的时候再设置其为 true. 这样进行采样的时候就进行了第一次压缩，叫做邻近采样。然后，对得到的 Bitmap 实例调用 `compress()` 方法，并指定一个图片的质量，通常是在 0~100 之间。这样就可以对图片的质量进行压缩，质量的值越大，图片质量越高，图片也越大。最后是双线性压缩，我们可以调用 Bitmap 的 `createScaledBitmap()` 或者调用 `createBitmap()` 并传入一个 Matrix 来实现。双线性压缩的好处是可以得到一个图片的固定的大小。

那么，在实际的使用过程中，我们可以根据我们的应用场景选择适合的压缩方式。比如，在我们的场景中，先使用尺寸压缩得到 Bitmap. 因为 Bitmap 太大的话，加载到内存中可能会发生 OOM. 然后，可以再使用双线性压缩把图片的尺寸压缩到固定的大小。最后就是使用质量压缩，通过降低图片质量来把降低图片的大小。

Android 中加载图片使用的就是 BitmapFactory 的一系列 `decode()` 方法，包括 Glide 内部也是从各种流中获取到一个 Bitmap. 这些 `decode()` 方法会调用 Native 的方法。Native 方法中的实现引用了 Skia 来实现。Skia 是谷歌的一个 2D 图像库，用 C++ 实现。它提供了适用于多种应用平台的公共 API. 并作为 Chrome, Android 等的图片处理引擎。

Skia 本身提供了基本的画图和编解码功能，它同时还挂载了其他第三方编解码库，例如：libpng.so、libjpeg.so、libgif.so. 指定类型的图片将交给指定类型的编码库来完成。

Skia 的资料：[官方网址](https://skia.org/)、[Github 地址](https://github.com/google/skia) 以及[在 Android 源码中的位置](https://android.googlesource.com/platform/external/skia/)。

- 大图加载
- 图片加载库相关，Bitmap 如何处理大图，如一张 30M 的大图，如何预防 OOM

使用 BitmapRegionDecoder 来部分加载图片。加载图片的时候调用下面的方法即可，

```java
public Bitmap decodeRegion(Rect rect, BitmapFactory.Options options)
```

在控件的 `onDraw()` 方法中进行绘制，然后使用 GestureDetector 来检查手势，当移动图片的时候调用 `invalid()` 方法进行重绘即可。

- 几种图片加载框架的对比

1. Glide 比 Picasso 包体积更大，方法数更多，使用的时候需要开启混淆。
2. Glide 相比 Picasso 的一大优势是它可以和 Activity 以及 Fragment 的生命周期相互协作。
3. Picasso 将图片下载后会不经压缩直接将图片整个缓存到磁盘中，当需要用到图片时，它会直接返回这张完整大小的图片，并在运行时根据 ImageView 的大小作适配。而 Glide 会综合要显式的图片的尺寸和变换等信息，构建一个缓存的 Key，然后将变换之后的图片缓存。此外，它还提供了可配置的缓存策略。
4. 在加载同样配置的图片时，Glide 内存占用更少，这从前面的讨论中其实可以猜测到了，Picasso 是将完整大小的图片加载进内存，然后依赖 GPU 来根据 ImageView 的大小来适配并渲染图片，而 Glide 是针对每个 ImageView 适配图片大小后再存储到磁盘的，这样加载进内存的是压缩过的图片，内存占用自然就比 Picasso 要少。
5. 图片加载的耗时：Picasso 相比 Glide 要快很多。可能的原因是 Picasso 下载完图片后直接将整个图片加载进内存，而 Glide 还需要针对每个 ImageView 的大小来适配压缩下载到的图片，这个过程需要耗费一定的时间。
6. Glide 独有的特性：对 GIF 动画的支持，缩略图的支持。
7. 默认使用ARGB_8888格式缓存图片, 缓存体积大；Glide 默认使用 RGB565.

Fresco - Facebook：

1. 图片存储在安卓系统的匿名共享内存, 而不是虚拟机的堆内存中, 图片的中间缓冲数据也存放在本地堆内存, 所以, 应用程序有更多的内存使用，不会因为图片加载而导致 OOM, 同时也减少垃圾回收器频繁调用回收 Bitmap 导致的界面卡顿，性能更高。
2. 图片可以以任意的中心点显示在 ImageView, 而不仅仅是图片的中心。
3. JPEG 图片改变大小也是在 native 进行的, 不是在虚拟机的堆内存, 同样减少 OOM；
4. 很好的支持 GIF 图片的显示

缺点: 1). 框架较大, 影响Apk体积；2). 使用较繁琐。

ImageLoader：

1. 支持下载进度监听
2. 可以在 View 滚动中暂停图片加载 
3. 默认实现多种内存缓存算法这几个图片缓存都可以配置缓存算法，不过 ImageLoader 默认实现了较多缓存算法，如 Size 最大先删除、使用最少先删除、最近最少使用、先进先删除、时间最长先删除等。
4. 支持本地缓存文件名规则定义

缺点: 缺点在于不支持 GIF 图片加载,  缓存机制没有和 http 的缓存很好的结合, 完全是自己的一套缓存机制。

## 3、EventBus

- EventBus 作用，实现方式，代替 EventBus 的方式
- EventBus 实现原理？

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

- RxJava 的作用，优缺点
- RxJava 的作用，与平时使用的异步操作来比，优势
- RxJava 简介及其源码解读？



## 5、数据库

- 数据库框架对比和源码分析？
- SQLite 数据库升级，数据迁移问题？
- SQLite 升级，增加字段的语句
- 数据库数据迁移问题

数据库框架：Room 出现之前使用最多的是 OrmLite 和 GreenDAO. ORMLite 和 JavaWeb 框架中的 Hibernate 相似，都是使用注解的方式来标注数据库中的表、字段、关联关系的，这也是 ORMLite 的工作原理：ORMLite 是基于反射机制工作的，然而这也成为了 ORMLite 的一个非常致命的缺点，性能不好。因此，如果是对想能要求不高的项目，我们可以考虑使用 ORMLite，而如果项目对性能要求较高，我们可以考虑使用 GreenDAO. 

个人之前也搭建过简单的数据库框架，只是在 Android 的基础的 SQLite 类基础之上做了一层封装。我也是使用注解，但是对注解的解析只在第一次创建表的时候使用。对 ContentValue 和 Cursur 操作的时候使用的是它们的 setter 和 getter 进行赋值。顶层对基础的查询等操作做了封装，各个子类只需要实现模板的方法，无需写 SQL 就可以实现数据库的基本操作。但是 setter 和 getter 的过程比较繁琐，而且这种重复性的工作是应该交给程序来解决的。这个问题可以使用注解处理进行优化，在编译器期间按规则把这些逻辑写好。（没做的主要原因是出现了 Room，没必要重复造轮子了。）

GreenDao 相较于 ORMLite 等其他数据库框架有以下优势：更精简；性能最大化；内存开销最小化；易于使用的 APIs；对 Android 进行高度优化。Android 中使用比较常见的注解处理器是 APT，但是 GreenDao 使用的是 JDT。代码生成框架使用的是 FreeMarker，对应的还有一种生成代码的方式叫 javapoet 的。GreenDAO 也是通过在编译期间生成代码来完成数据库的字段到对象的属性的映射。

**数据库升级**，

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

**打开 db 文件**，

## 其他

- 用到的一些开源框架，介绍一个看过源码的，内部实现过程
- ButterKnife 实现原理？

ButterKnife 基于注解处理，在运行时根据 Activity 的名称生成对应的注射类。可以在 Activity 名称后面增加 `$Injector` 的方式来实现。然后会根据这个类中使用的注解绑定的控件和事件，在编译期间赋值的代码。当我们调用 `bind()` 方法的时候，根据当前的类名寻找注射器。然后调用它的 `inject()` 方法，在这个生成的方法中去执行 `findViewById()` 和 `setOnClickListener()` 等工作。

