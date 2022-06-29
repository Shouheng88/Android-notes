# Android [DEPRECATED]

## 1、目录

### 基础开发

- 基础回顾
    - [Android 基础回顾：Activity 基础](四大组件/Activity.md)
    - [Android 基础回顾：Fragment 基础](四大组件/Fragment.md)
    - [Android 基础回顾：Service 基础](四大组件/Service.md)
    - [Android 基础回顾：Broadcast 基础](四大组件/Broadcast.md)

- 开发语言
    - [Java 注解在 Android 中的应用](注解和依赖注入/注解在Android中的应用.md)
    - [Kotlin 基础知识梳理](Kotlin/Kotlin.md)
    - [在 Android 中使用 JNI 的总结](高阶技术/JNI技术总结.md)

- 架构设计
    - [Android 应用架构设计探索：MVC、MVP、MVVM和组件化](结构设计/探索Android架构设计.md)
    - [浅谈 ViewModel 的生命周期控制](高阶技术/浅谈ViewModel生命周期控制.md)
    - [浅谈 LiveData 的通知机制](高阶技术/浅谈LiveData的通知过程.md)

- 性能优化
    - [ANR](性能优化/Android性能优化-ANR.md)
    - [布局优化](性能优化/Android性能优化-布局优化.md)
    - [进程保活](性能优化/Android进程保活.md)
    - [启动优化](性能优化/Android性能优化-启动优化.md)
    - [内存优化](性能优化/Android性能优化-内存优化.md)

- 开发环境
    - [常见的 ADB 指令总结](开发工具/ADB_常见的ADB指令总结.md)
    - [常见的 Gradle 指令和配置总结](开发工具/Gradle_常见的指令和配置总结.md)
    - [常见的 Keytool 指令总结](开发工具/Keytool_常用的指令.md)

### 系统源码

- 核心流程
    - [Android 系统架构](系统架构/Android系统架构.md)
    - [Android 系统启动流程源码分析](系统架构/Android系统启动过程.md)
    - [Android 应用打包过程](系统架构/Android打包过程.md)
    - [Android 应用安装过程](系统架构/Android应用安装过程.md)

- 消息机制
    - [Android 消息机制：Handler、MessageQueue 和 Looper](消息机制/线程通信：Handler、MessageQueue和Looper.md.md)
    - [Android IPC 机制：Binder 机制](消息机制/跨进程通信：Binder机制.md) 

- 异步编程
    - [AsyncTask 的使用和源码分析](异步编程/AsyncTask源码分析.md)
    - [Android 多线程编程：IntentService 和 HandlerThread](异步编程/Android多线程编程：IntentService和HandlerThread.md)

- 窗口机制
    - [Android 的窗口管理机制](系统架构/窗口机制/Android的Window管理机制.md)（编辑中）

- 控件体系
    - [View 体系详解：View的工作流程](系统架构/控件体系/View体系详解：View的工作流程.md)
    - [View 体系详解：坐标系、滑动事件和分发机制](系统架构/控件体系/View体系详解：坐标系、滑动事件和分发机制.md)
    - [Android 动画体系详解](系统架构/控件体系/动画体系详解.md)
    - [SurfaceView 与 TextureView 的区别](系统架构/SurefaceView_and_TextureView.md)

- 部分 API 源码
    - [LruCache 的使用和源码分析](API简析/LruCache.md)

### 三方库源码

- 网络框架
    - [网络框架 OkHttp 源码解析](网络访问/OKHttp源码阅读.md)
    - [网络框架 Retrofit 源码解析](网络访问/Retrofit源码阅读.md)

- 图片加载框架
    - [Glide 系列-1：预热、Glide 的常用配置方式及其原理](图片加载/Glide系列：Glide的配置和使用方式.md)
    - [Glide 系列-2：主流程源码分析](图片加载/Glide系列：Glide主流程源码分析.md)
    - [Glide 系列-3：Glide 缓存的实现原理](图片加载/Glide系列：Glide的缓存的实现原理.md)

- RxJava
    - [RxJava2 系列-1：一篇的比较全面的 RxJava2 方法总结](响应式编程/RxJava2系列·_一篇的比较全面的RxJava2方法总结.md)
    - [RxJava2 系列-2：Flowable 和背压](响应式编程/Flowable和背压.md)
    - [RxJava2 系列-3：使用 Subject](响应式编程/用RxJava打造EventBus.md)
    - [RxJava2 系列-4：RxJava 源码分析](响应式编程/RxJava系列-4：RxJava源码分析.md)

- 其他框架
    - [消息机制 EventBus 源码解析](消息机制/EventBus的源码分析.md)
    - [Dagger 从集成到源码带你理解依赖注入框架](高阶技术/Dagger从集成到源码.md)

### Java 相关

- 并发编程
    - [Java 并发编程：ThreadLocal 的使用及其源码实现](https://blog.csdn.net/github_35186068/article/details/83858944)

- 设计模式
    - [观察者模式](https://blog.csdn.net/github_35186068/article/details/83754026)

- 虚拟机
    - [内存管理](https://juejin.im/post/5b475e976fb9a04fa8671a45)
    - [虚拟机执行子系统](https://juejin.im/post/5b4a1fb7e51d4519213fd374)
    - [虚拟机内存模型与高效并发](https://juejin.im/post/5b4f48e75188251b1b448aa0)

- 三方库
    - [时间库 JodaTime](https://blog.csdn.net/github_35186068/article/details/83754146)

### UI 相关

- [自定义控件](系统架构/控件体系/View体系详解：自定义控件.md)（编辑中）

### 编程基础

- 数据库
    - [MySQL 基础知识（全）](https://juejin.im/post/5a12d62bf265da431d3c4a01)

### 面试题

> 通过面试题梳理知识点细节

- [Android高级面试_1_Handler相关](笔试面试/Android高级面试_1_Handler相关.md)
- [Android高级面试_2_IPC相关](笔试面试/Android高级面试_2_IPC相关.md)
- [Android高级面试_3_语言相关](笔试面试/Android高级面试_3_语言相关.md)
- [Android高级面试_4_虚拟机相关](笔试面试/Android高级面试_4_虚拟机相关.md)
- [Android高级面试_5_四大组件、系统源码等](笔试面试/Android高级面试_5_四大组件、系统源码等.md)
- [Android高级面试_6_性能优化](笔试面试/Android高级面试_6_性能优化.md)
- [Android高级面试_7_三方库相关](笔试面试/Android高级面试_7_三方库相关.md)
- [Android高级面试_8_热修补插件化等](笔试面试/Android高级面试_8_热修补插件化等.md)
- [Android高级面试_9_网络基础](笔试面试/Android高级面试_9_网络基础.md)
- [Android高级面试_10_跨平台开发](笔试面试/Android高级面试_10_跨平台开发.md)
- [Android高级面试_11_JNINDK](笔试面试/Android高级面试_11_JNINDK.md)
- [Android高级面试_12_项目经验梳理](笔试面试/Android高级面试_12_项目经验梳理.md)
- [Android 中高级工程师面试题总结](笔试面试/Android高级软件工程师2017.md)

### 其他

- [马克笔记—Android 端开源的 Markdown 笔记应用](其他/MarkNote版本1的.md)
- [承上启下：Markdown 笔记应用 MarkNote 的重构之路](其他/MarkNote版本2.md)

## 2、资源整理


