## JVM

- [ ] 谈谈类加载器classloader
- [ ] 动态加载
- [ ] GC回收策略
- [ ] Java中对象的生命周期
- [ ] 类加载机制，双亲委派模型
- [ ] JVM 内存区域 开线程影响哪块内存
- [ ] 垃圾收集机制 对象创建，新生代与老年代
- [ ] JVM内存模型
- [ ] 垃圾回收机制与调用System.gc()区别
- [ ] 软引用、弱引用区别
- [ ] 垃圾回收
- [ ] java四中引用
- [ ] 垃圾收集器
- [ ] 强引用置为null，会不会被回收？
- [ ] Java中内存区域与垃圾回收机制
- [ ] OOM，内存泄漏
- [ ] JVM内存模型，内存区域

## Android系统

## 1、四大组件

### 1.1 Activity

在两个 Activity 之间传递对象还需要注意什么呢？
对象的大小，对象的大小，对象的大小！！！
重要的事情说三遍，一定要注意对象的大小。Intent 中的 Bundle 是使用 Binder 机制进行数据传送的。能使用的 Binder 的缓冲区是有大小限制的（有些手机是 2 M），而一个进程默认有 16 个 Binder 线程，所以一个线程能占用的缓冲区就更小了（ 有人以前做过测试，大约一个线程可以占用 128 KB）。所以当你看到 The Binder transaction failed because it was too large 这类 TransactionTooLargeException 异常时，你应该知道怎么解决了。

### 1.2 Service

### 1.3 Broadcast

### 1.4 ContentProvider

### 1.5 Fragment

## 2、API 源码

### 2.1 AsyncTask

1. **AsyncTask 机制，如何取消 AsyncTask**
2. **多线程（关于 AsyncTask 缺陷引发的思考）**
3. **Asynctask 有什么优缺点**

AsyncTask 是 Android 提供的用来执行异步操作的 API，我们可以通过它来执行异步操作，并在得到结果之后将结果放在主线程当中进行后续处理。

AsyncTask 的缺点是在使用多个异步操作和并需要进行 Ui 变更时，就变得复杂起来（会导致多个 AsyncTask 进行嵌套）。如果有多个地方需要用到 AsyncTask，可能需要定义多个 AsyncTask 的实现。

如果 AsyncTask 以一个非静态的内部类的形式声明在 Activity 中，那么它会持有 Activity 的匿名引用，如果销毁 Activity 时 AsyncTask 还在执行异步任务的话，Activity 就不能销毁，会造成内存泄漏。解决方式是，要么将 AsyncTask 定义成静态内部类，要么在 Activity 销毁的时候调用 `cancel()` 方法取消 AsyncTask.在屏幕旋转或 Activity 意外结束时，Activity 被创建，而 AsyncTask 会拥有之前 Activity 的引用，会导致结果丢失。

AsyncTask 在 1.6 之前是串行的，1.6 之后是并行的，3.0 之后又改成了串行的。不过我们可以通过调用 `executeOnExecutor()` 方法并传入一个线程池，来让 AsyncTask 在某个线程池中并行执行任务。

AsyncTask 的源码就是将一个任务封装成 Runnable 之后放进线程池当中执行，执行完毕之后调用主线程的 Handler 发送消息到主线程当中进行处理。任务在默认线程池当中执行的时候，会被加入到一个双端队列中执行，执行完一个之后再执行下一个，以此来实现任务的串行执行。


- [ ] 热修复,插件化
- [ ] 性能优化，怎么保证应用启动不卡顿
- [x] SP是进程同步的吗?有什么方法做到同步

    非进程同步，使用 MODE_MULTI_PROCESS 可以设置进程同步的，但是不可靠，原理是将数据加载到内存中，写入之前强制读取，当 commit 高频的时候仍然会造成数据不同步。    
    使用 ContentProvider 来模拟 SharedPreferences. SP 的增删对应数据库的增删。

- [ ] 介绍下 SurfView
- [ ] 图片加载原理
- [ ] 模块化实现（好处，原因）
- [ ] 视频加密传输
- [ ] 统计启动时长,标准
- [ ] 如何保持应用的稳定性
- [ ] BroadcastReceiver，LocalBroadcastReceiver 区别
- [ ] Android 事件分发机制
- [ ] App 启动流程，从点击桌面开始
- [ ] 画出 Android 的大体架构图
- [ ] 描述清点击 Android Studio 的 build 按钮后发生了什么
- [ ] 大体说清一个应用程序安装到手机上时发生了什么
- [ ] 对 Dalvik、ART 虚拟机有基本的了解
- [ ] App 是如何沙箱化，为什么要这么做
- [ ] 权限管理系统（底层的权限是如何进行 grant 的）
- [ ] 进程和 Application 的生命周期
- [ ] 系统启动流程 Zygote进程 –> SystemServer进程 –> 各种系统服务 –> 应用进程
- [ ] recycleview listview 的区别,性能
- [ ] 进程调度
- [ ] 线程和进程的区别？
- [ ] 动态权限适配方案，权限组的概念
- [ ] 图片加载库相关，bitmap 如何处理大图，如一张 30M 的大图，如何预防 OOM
- [ ] 进程保活
- [ ] 广播（动态注册和静态注册区别，有序广播和标准广播）
- [ ] listview 图片加载错乱的原理和解决方案
- [ ] service 生命周期
- [ ] 数据库数据迁移问题
- [ ] 是否熟悉 Android jni 开发，jni 如何调用 java 层代码
- [ ] 计算一个 view 的嵌套层级
- [ ] 项目组件化的理解
- [ ] Android 系统为什么会设计 ContentProvider，进程共享和线程安全问题
- [ ] Android 相关优化（如内存优化、网络优化、布局优化、电量优化、业务优化） 
- [ ] EventBus 实现原理
- [ ] 四大组件
- [ ] Android 中数据存储方式
- [ ] ActicityThread 相关？
- [ ] Android 中进程内存的分配，能不能自己分配定额内存
- [x] ViewPager 使用细节，如何设置成每次只初始化当前的 Fragment，其他的不初始化

    不能使用 ViewPager.setOffscreenPageLimit(0)，其最小值为1. 

- [ ] ListView 重用的是什么
- [ ] 应用安装过程
- [ ] fragment 之间传递数据的方式？
- [ ] OOM 的可能原因？
- [ ] 为什么要有线程，而不是仅仅用进程？
- [ ] 内存泄漏的可能原因？
- [ ] 用 IDE 如何分析内存泄漏？
- [ ] 触摸事件的分发？
- [ ] 简述 Activity 启动全部过程？
- [ ] 性能优化如何分析 systrace？
- [ ] 广播的分类？
- [ ] 点击事件被拦截，但是相传到下面的 view，如何操作？
- [ ] 如何保证多线程读写文件的安全？
- [ ] Activity 启动模式
- [ ] 广播的使用方式，场景
- [ ] App 中唤醒其他进程的实现方式
- [ ] Android 中开启摄像头的主要步骤
- [ ] Activity 生命周期
- [ ] AlertDialog, popupWindow, Activity 区别
- [ ] fragment 各种情况下的生命周期
- [ ] Activity 上有 Dialog 的时候按 home 键时的生命周期
- [ ] 横竖屏切换的时候，Activity 各种情况下的生命周期
- [ ] Application 和 Activity 的 context 对象的区别
- [ ] ANR 怎么分析解决
- [x] LinearLayout、RelativeLayout、FrameLayout 的特性、使用场景

    ...

- [ ] 如何实现 Fragment 的滑动
- [ ] AndroidManifest 的作用与理解
- [ ] Jni 用过么？
- [ ] 多进程场景遇见过么？
- [ ] sqlite 升级，增加字段的语句
- [ ] bitmap recycler 相关
- [ ] Activity 与 Fragment 之间生命周期比较
- [ ] 广播的使用场景
- [ ] Bitmap 使用时候注意什么？
- [ ] Oom 是否可以 try catch ？

内存优化    


- [ ] 内存泄露如何产生？
- [ ] 适配器模式，装饰者模式，外观模式的异同？
- [ ] 如何保证线程安全？
- [ ] 事件传递机制的介绍
- [ ] View 事件传递
- [ ] activity 栈
- [ ] 封装 view 的时候怎么知道 view 的大小
- [ ] 怎么启动 service，service 和 activity 怎么进行数据交互
- [ ] 下拉状态栏是不是影响 activity 的生命周期，如果在 onStop 的时候做了网络请求，onResume 的时候怎么恢复
- [ ] view 渲染
- [ ] singleTask 启动模式
- [ ] 消息机制实现
- [ ] App 启动崩溃异常捕捉
- [ ] ListView 的优化
- [ ] Android 进程分类
- [ ] 前台切换到后台，然后再回到前台，Activity 生命周期回调方法。弹出 Dialog，生命值周期回调方法。
- [ ] RecycleView的 使用，原理，RecycleView 优化
- [ ] ANR 的原因
- [ ] Service 的开启方式
- [ ] Activity 与 Service 通信的方式
- [ ] Activity 之间的通信方式

## 网络

- [ ] Https 请求慢的解决办法，DNS，携带数据，直接访问IP
- [ ] TCP/UDP 的区别
- [ ] https 相关，如何验证证书的合法性，https 中哪里用了对称加密，哪里用了非对称加密，对加密算法（如RSA）等是否有了解
- [ ] TCP 与 UDP 区别与应用（三次握手和四次挥手）涉及到部分细节（如 client 如何确定自己发送的消息被 server 收到） HTTP 相关 提到过 Websocket 问了 WebSocket 相关以及与socket的区别
- [ ] 多线程断点续传原理

## 算法

- [ ] 排序，快速排序的实现
- [ ] 树：B 树、B+ 树的介绍
- [ ] 图：有向无环图的解释
- [ ] 二叉树 深度遍历与广度遍历
- [ ] 常用数据结构简介
- [ ] 判断环（猜测应该是链表环）
- [ ] 排序，堆排序实现
- [ ] 链表反转
- [ ] x 个苹果，一天只能吃一个、两个、或者三个，问多少天可以吃完
- [ ] 堆排序过程，时间复杂度，空间复杂度
- [ ] 快速排序的时间复杂度，空间复杂度
- [ ] 翻转一个单项链表
- [ ] 两个不重复的数组集合中，求共同的元素
- [ ] 上一问扩展，海量数据，内存中放不下，怎么求出
- [ ] 合并多个单有序链表（假设都是递增的）
- [ ] 算法判断单链表成环与否？
- [ ] 二叉树，给出根节点和目标节点，找出从根节点到目标节点的路径
- [ ] 一个无序，不重复数组，输出 N 个元素，使得 N 个元素的和相加为 M，给出时间复杂度、空间复杂度。手写算法
- [ ] 数据结构中堆的概念，堆排序

常见的算法题！！！！

## 第三方库

- [ ] 网络请求缓存处理，okhttp如何处理网络缓存的
- [ ] RxJava的作用，与平时使用的异步操作来比，优势
- [ ] RxJava的作用，优缺点
- [ ] Glide源码？
- [ ] okhttp源码？
- [ ] RxJava简介及其源码解读？
- [ ] glide 使用什么缓存？
- [ ] Glide 内存缓存如何控制大小？
- [ ] 用到的一些开源框架，介绍一个看过源码的，内部实现过程
- [ ] EventBus作用，实现方式，代替EventBus的方式

volley 

以及各种库的设计的优缺点

库主要集中在 图片加载的几种库的对比，网络访问的几种库的对比

EventBus 库的源码要清晰！！


## 设计模式

- [ ] 设计模式相关（例如Android中哪里使用了观察者模式，单例模式相关）
- [ ] MVP模式
- [ ] Java设计模式，观察者模式
- [ ] 模式MVP，MVC介绍

参考：

1. 整理自：[知乎 Misssss Cathy 的回答](https://zhuanlan.zhihu.com/p/30016683)


深度研究：

1. SurefaceView, TextureView, Camera
2. RecyclerView
3. Adapter + Fragment

热修补+插件化（组件化）

PMW WMS AMW 相关的东西

优化经验：

1. ANR 处理
2. 相机优化
3. RV 优化
4. 其他的优化
5. 逻辑优化


## 网络

TCP UDP HTTP HTTP2 HTTPS

以及 HTTP 的各种概念

## 项目相关

以上的深度研究 + 屏幕适配方式 + WorkManager 的研究

权限机制的底层原理