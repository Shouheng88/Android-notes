# Android 高级面试-7：三方库相关

*同种功能的类库之间对比，同时分析各种库的设计的优缺点*

## 1、网络框架

- OkHttp 源码？
- 网络请求缓存处理，OkHttp 如何处理网络缓存的

首先从整体的架构上面看，OkHttp 是基于`责任链设计模式`设计的，责任链的每一个链叫做一个拦截器，OkHttp 的请求是依次通过`重试、桥接、缓存、连接和访问服务器`五个责任链，分别用来进行错误重连，从缓存中获取请求结果，建立服务器连接以及最终从服务器中拿到请求结果。其中当从缓存中拿到了结果的时候，可以直接返回缓存中的结果而无需从服务器中获取请求。除此之外，我们还可以自定义自己的拦截器。

重试阶段主要是根据请求的响应重试次数等，来决定是否应该继续应该重新发送请求。如果服务器返回了重定向信息，那么它还要使用新的地址重新获取数据。

OkHttp 的缓存最终是使用的 DiskLruCache 将请求的请求和响应信息存储到磁盘上。当进入到缓存拦截器的时候，首先会先从缓存当中获取请求的请求信息和响应信息。它会从响应信息的头部获取本次请求的缓存信息，比如过期时间之类的，然后判断该响应是否可用。如果可用，或者处于未连网状态等，则将其返回。否则，再从网络当中获取请求的结果。当拿到了请求的结果之后，还会再次回到缓存拦截器。缓存拦截器在拿到了响应之后，再根据响应和请求的信息决定是否将其持久化到磁盘上面。

OkHttp 的网络访问并没有直接使用 HttpUrlConnection 或者 HttpClient 而是直接使用 Socket 建立网络连接。在拿到一个请求的时候，OkHttp 首先会到连接池中寻找可以复用的连接。这里的连接池是使用双端队列维护的一个列表。当从连接池中获取到一个连接之后就使用它来进行网络访问。

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

- HttpUrlConnection 和 HttpClient 的区别？

## 2、图片加载框架

- Glide 源码？
- Glide 使用什么缓存？
- Glide 内存缓存如何控制大小？
- 图片加载原理？
- 图片压缩原理？
- 图片框架实现原理？LRUCache 原理？
- 对 Bitmap 对象的了解？

## 3、EventBus

- EventBus 作用，实现方式，代替EventBus的方式
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


volley 



库主要集中在 图片加载的几种库的对比，网络访问的几种库的对比

EventBus 库的源码要清晰！！