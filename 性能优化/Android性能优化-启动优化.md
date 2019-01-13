# Android 性能优化 - 启动优化

## 1、基础

### 1.1 启动的类型

首先是启动的三种类型：

1. **冷启动场景**：后台完全没有任何进程的情况下，启动最慢；
2. **温启动场景**：按返回键退回主界面再从主界面打开的情形，较快；
3. **热启动场景**：按 Home 键退回到主界面再从主界面打开的情形，最快。

应用启动的过程实际上也就是 Activity 启动的流程，所以具体涉及的源码不是我们这里的重点，你可以查找 Activity 启动流程相关的文章来了解源码。

其实优化应用的启动速度无非也就是在那几个生命周期方法中进行优化，不做太多耗时操作等：Application 的生命周期和 Activity 的生命周期。

### 1.2 启动速度的测量

当然，我们而已通过自己的感觉判断启动的快慢，但量化还是非常重要的，不然你都无法向 PM 交差不是。所以，我们有必要了解下 Android 中的启动速度是如何测量的。

#### 方式 1：使用 ADB

获取启动速度的第一种方式是使用 ADB，使用下面的指令的时候在启动应用的时候会使用 AMS 进行统计。但是缺点是统计时间不够准确：

```shell
adb shell am start -n ｛包名｝/｛包名｝.{活动名}
```

#### 方式 2：代码埋点

在 Application 的 `attachBaseContext()` 方法中记录开始时间，第一个 Activity 的 `onWindowFocusChanged()` 中记录结束时间。缺点是统计不完全，因为在 `attachBaseContext()` 之前还有许多操作。

#### 方式 3：TraceView

在 AS 中打开 DDMS，或者到 SDK 安装目录的 tools 目录下面使用 `monitor.bat` 打开 DDMS。

TraceView 工具的使用可以参考这篇文章：[《Android 性能分析之TraceView使用(应用耗时分析)》](https://blog.csdn.net/android_jianbo/article/details/76608558)

通过 TraceView 主要可以得到两种数据：单次执行耗时的方法以及执行次数多的方法。但 TraceView 性能耗损太大，不能比较正确反映真实情况。

#### 方式 4：Systrace

Systrace 能够追踪关键系统调用的耗时情况，如系统的 IO 操作、内核工作队列、CPU 负载、Surface 渲染、GC 事件以及 Android 各个子系统的运行状况等。但是不支持应用程序代码的耗时分析。

#### 方式 5：Systrace + 插桩

类似于 AOP，通过切面为每个函数统计执行时间。这种方式的好处是能够准确统计各个方法的耗时。

原理就是

```java
    public void method() {
        TraceMethod.i();
        // Real work
        TraceMethod.o();
    }
```

## 2、启动优化

### 2.1 一般解决办法

#### 2.1.1 延迟初始化

一些逻辑，如果没必要在程序启动的时候就立即初始化，那么可以将其推迟到需要的时候再初始化。比如，我们可以使用单例的方式来获取类的实例，然后在获取实例的时候再进行初始化操作。

**但是需要注意的是，懒加载要防止集中化，否则容易出现首页显示后用户无法操作的情形。可以按照耗时和是否必要将业务划分到四个维度：必要且耗时，必要不耗时，非必要但耗时，非必要不耗时。**然后对应不同的维度来决定是否有必要在程序启动的时候立即初始化。

#### 2.1.2 防止主线程阻塞

一般我们也不会把耗时操作放在主线程里面，毕竟现在有了 RxJava 之后，在程序中使用异步代价并不高。这种耗时操作包括，大量的计算、IO、数据库查询和网络访问等。

另外，关于开启线程池的问题下面的话总结得比较好，除了一般意义上线程池和使用普通线程的区别，还要考虑应用启动这个时刻的特殊性：

> 如何开启线程同样也有学问：Thread、ThreadPoolExecutor、AsyncTask、HandlerThread、IntentService 等都各有利弊；例如通常情况下 ThreadPoolExecutor 比 Thread 更加高效、优势明显，但是特定场景下单个时间点的表现 Thread 会比 ThreadPoolExecutor 好：同样的创建对象，ThreadPoolExecutor 的开销明显比 Thread 大。
>   
> 来自：https://www.jianshu.com/p/f5514b1a826c  

#### 2.1.3 布局优化

比如，之前我在使用 Fragment 和 ViewPager 搭配的时候，发现虽然 Fragment 可以被复用，但是如果通过 Adapter 为 ViewPager 的每个项目指定了标题，那么这些标题控件不会被复用。当 ViewPager 的条目比较多的时候，甚至会造成 ANR. 

对于这种布局优化相关的东西，可以参考性能优化的 [Android性能优化-布局优化](Android性能优化-布局优化.md) 模块。

#### 2.1.4 使用启动页面防止白屏

这种方法只是治标不治本的方法，就是在应用启动的时候避免白屏，可以通过设置自定义主题来实现。

这种实现方式可以参考我的开源项目 [MarkNote](https://github.com/Shouheng88/MarkNote) 的实现。

#### 2.1.5 使用 BlockCanary 检测卡顿

BlockCanary 是一个开源项目，类似于 LeakCanary （很多地方也借鉴了 LeakCanary 的东西），主要用来检测程序中的卡顿，项目地址是 [Github-BlockCanary](https://github.com/markzhai/AndroidPerformanceMonitor). 它的原理是对 Looper 中的 `loop()` 方法打处的日志进行处理，通过一个自定义的日志输出 Printer 监听方法执行的开始和结束。可以通过该项目作者的文章来了解这个项目：

[BlockCanary — 轻松找出Android App界面卡顿元凶](https://www.jianshu.com/p/cd7fc77405ac)

#### 2.1.6 GC 优化

GC 优化的思想就是减少垃圾回收的时间间隔，所以在启动的过程中不要频繁创建对象，特别是大对象，避免进行大量的字符串操作，特别是序列化跟反序列化过程。一些频繁创建的对象，例如网络库和图片库中的 Byte 数组、Buffer 可以复用。如果一些模块实在需要频繁创建对象，可以考虑移到 Native 实现。



参考资料：

- [App startup time](https://developer.android.com/topic/performance/vitals/launch-time)
- [Android性能优化（一）之启动加速35%](https://www.jianshu.com/p/f5514b1a826c)
