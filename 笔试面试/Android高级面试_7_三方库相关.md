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

Volley 封装了访问网络的一些操作，但是底层在 Android 2.3 及以上版本，使用的是 HttpURLConnection，而在 Android 2.2 及以下版本，使用的是HttpClient. 

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

Volley 封装了访问网络的一些操作，但是底层在 Android 2.3 及以上版本，使用的是 HttpURLConnection，而在 Android 2.2 及以下版本，使用的是HttpClient. 

当使用 RequestQueue 构建一个请求队列之后，它首先会根据系统版本选择使用 HttpURLConnection 或者 HttpClient 作为底层实现。

## 2、图片加载框架

- Glide 源码？
- Glide 使用什么缓存？
- Glide 内存缓存如何控制大小？
- 图片加载原理？
- 图片压缩原理？
- 图片框架实现原理？LRUCache 原理？
- 对 Bitmap 对象的了解？

## 3、EventBus

- EventBus 作用，实现方式，代替 EventBus 的方式
- EventBus 实现原理？

## 4、RxJava

- RxJava 的作用，优缺点
- RxJava 的作用，与平时使用的异步操作来比，优势
- RxJava 简介及其源码解读？

## 5、数据库

- 数据库框架对比和源码分析？
- SQLite 数据库升级，数据迁移问题？

## 其他

- 用到的一些开源框架，介绍一个看过源码的，内部实现过程
- ButterKnife 实现原理？



库主要集中在 图片加载的几种库的对比，网络访问的几种库的对比

EventBus 库的源码要清晰！！