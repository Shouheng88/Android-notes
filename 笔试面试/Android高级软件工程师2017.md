## Java和Android API

- [ ] SpareArray原理
- [x] LRUCache原理：[LruCache源码分析](https://juejin.im/post/5afc29caf265da0b7f44be28)
- [ ] HashMap实现原理，ConcurrentHashMap 的实现原理
- [x] ThreadLocal原理：[ThreadLocal的使用及其源码实现](https://juejin.im/post/5b44cd7c6fb9a04f980cb065)
- [ ] 线程间 操作 List
- [ ] OSGI
- [ ] synchronized与Lock的区别
- [ ] 抽象类和接口的区别
- [ ] 集合 Set实现 Hash 怎么防止碰撞 
- [ ] 死锁，线程死锁的4个条件？
- [ ] 进程状态
- [ ] 并发集合了解哪些
- [ ] CAS介绍
- [ ] volatile用法
- [ ] 开启线程的三种方式,run()和start()方法区别
- [ ] Java线程池
- [ ] 多线程（关于AsyncTask缺陷引发的思考）
- [ ] java注解 
- [ ] static synchronized 方法的多线程访问和作用，同一个类里面两个synchronized方法，两个线程同时访问的问题
- [ ] 对Java中String的了解
- [ ] ArrayList与LinkedList区别
- [ ] string to integer
- [ ] volatile的原理
- [ ] synchronize的原理
- [ ] lock原理
- [ ] AsyncTask机制，如何取消AsyncTask
- [ ] 手写生产者/消费者模式
- [ ] 如何实现线程同步？
- [ ] arraylist 与 linkedlist 异同？
- [ ] object类的equal 和hashcode 方法重写，为什么？
- [ ] hashmap如何put数据（从hashmap源码角度讲解）？
- [ ] String 为什么要设计成不可变的？
- [ ] List 和 Map 的实现方式以及存储方式。
- [ ] 静态内部类的设计意图
- [ ] 线程如何关闭，以及如何防止线程的内存泄漏
- [ ] NIO
- [ ] List,Set,Map的区别
- [ ] HashSet与HashMap怎么判断集合元素重复
- [ ] wait/notify
- [ ] 多线程：怎么用、有什么问题要注意；Android线程有没有上限，然后提到线程池的上限
- [ ] ReentrantLock 、synchronized和volatile（n面）
- [ ] Java中同步使用的关键字，死锁
- [ ] HashMap的实现，与HashSet的区别
- [ ] 死锁的概念，怎么避免死锁
- [ ] 内部类和静态内部类和匿名内部类，以及项目中的应用
- [ ] ReentrantLock的内部实现
- [ ] String buffer 与string builder 的区别？
- [ ] 集合的接口和具体实现类，介绍
- [ ] TreeMap具体实现
- [ ] synchronized与ReentrantLock

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

- [ ] 动态布局
- [ ] 热修复,插件化
- [ ] 性能优化,怎么保证应用启动不卡顿
- [ ] 怎么去除重复代码
- [ ] SP是进程同步的吗?有什么方法做到同步
- [ ] 介绍下SurfView
- [ ] 图片加载原理
- [ ] 模块化实现（好处，原因）
- [ ] 视频加密传输
- [ ] 统计启动时长,标准
- [ ] 如何保持应用的稳定性
- [ ] BroadcastReceiver，LocalBroadcastReceiver 区别
- [ ] Bundle 机制
- [ ] Handler 机制
- [ ] Binder相关？
- [ ] Android事件分发机制
- [ ] App启动流程，从点击桌面开始
- [ ] 画出 Android 的大体架构图
- [ ] 描述清点击 Android Studio 的 build 按钮后发生了什么
- [ ] 大体说清一个应用程序安装到手机上时发生了什么
- [ ] 对 Dalvik、ART 虚拟机有基本的了解
- [ ] Android 上的 Inter-Process-Communication 跨进程通信时如何工作的
- [ ] App 是如何沙箱化，为什么要这么做
- [ ] 权限管理系统（底层的权限是如何进行 grant 的）
- [ ] 进程和 Application 的生命周期
- [ ] 系统启动流程 Zygote进程 –> SystemServer进程 –> 各种系统服务 –> 应用进程
- [ ] recycleview listview 的区别,性能
- [ ] 消息机制
- [ ] 进程调度
- [ ] 线程和进程的区别？
- [ ] 动态权限适配方案，权限组的概念
- [ ] 图片加载库相关，bitmap如何处理大图，如一张30M的大图，如何预防OOM
- [ ] 进程保活
- [ ] 广播（动态注册和静态注册区别，有序广播和标准广播）
- [ ] listview图片加载错乱的原理和解决方案
- [ ] service生命周期
- [ ] handler实现机制（很多细节需要关注：如线程如何建立和退出消息循环等等）
- [ ] 数据库数据迁移问题
- [ ] 是否熟悉Android jni开发，jni如何调用java层代码
- [ ] 进程间通信的方式
- [ ] 计算一个view的嵌套层级
- [ ] 项目组件化的理解
- [ ] Android系统为什么会设计ContentProvider，进程共享和线程安全问题
- [ ] Android相关优化（如内存优化、网络优化、布局优化、电量优化、业务优化） 
- [ ] EventBus实现原理
- [ ] 四大组件
- [ ] Android中数据存储方式
- [ ] ActicityThread相关？
- [ ] Android中进程内存的分配，能不能自己分配定额内存
- [ ] 序列化，Android为什么引入Parcelable
- [ ] 有没有尝试简化Parcelable的使用
- [ ] ViewPager使用细节，如何设置成每次只初始化当前的Fragment，其他的不初始化
- [ ] ListView重用的是什么
- [ ] 进程间通信的机制
- [ ] AIDL机制
- [ ] 应用安装过程
- [ ] 简述IPC？
- [ ] fragment之间传递数据的方式？
- [ ] OOM的可能原因？
- [ ] 为什么要有线程，而不是仅仅用进程？
- [ ] 内存泄漏的可能原因？
- [ ] 用IDE如何分析内存泄漏？
- [ ] 触摸事件的分发？
- [ ] 简述Activity启动全部过程？
- [ ] 性能优化如何分析systrace？
- [ ] 广播的分类？
- [ ] 点击事件被拦截，但是相传到下面的view，如何操作？
- [ ] 如何保证多线程读写文件的安全？
- [ ] Activity启动模式
- [ ] 广播的使用方式，场景
- [ ] App中唤醒其他进程的实现方式
- [ ] Android中开启摄像头的主要步骤
- [ ] Activity生命周期
- [ ] AlertDialog,popupWindow,Activity区别
- [ ] fragment 各种情况下的生命周期
- [ ] Activity 上有 Dialog 的时候按 home 键时的生命周期
- [ ] 横竖屏切换的时候，Activity 各种情况下的生命周期
- [ ] Application 和 Activity 的 context 对象的区别
- [ ] 序列化的作用，以及 Android 两种序列化的区别。
- [ ] ANR怎么分析解决
- [ ] LinearLayout、RelativeLayout、FrameLayout的特性、使用场景
- [ ] 如何实现Fragment的滑动
- [ ] AndroidManifest的作用与理解
- [ ] Jni 用过么？
- [ ] 多进程场景遇见过么？
- [ ] 关于handler，在任何地方new handler都是什么线程下
- [ ] sqlite升级，增加字段的语句
- [ ] bitmap recycler 相关
- [ ] Activity与Fragment之间生命周期比较
- [ ] 广播的使用场景
- [ ] Bitmap 使用时候注意什么？
- [ ] Oom 是否可以try catch ？
- [ ] 内存泄露如何产生？
- [ ] 适配器模式，装饰者模式，外观模式的异同？
- [ ] ANR 如何产生？
- [ ] 如何保证线程安全？
- [ ] 事件传递机制的介绍
- [ ] handler发消息给子线程，looper怎么启动
- [ ] View事件传递
- [ ] activity栈
- [ ] 封装view的时候怎么知道view的大小
- [ ] 怎么启动service，service和activity怎么进行数据交互
- [ ] 下拉状态栏是不是影响activity的生命周期，如果在onStop的时候做了网络请求，onResume的时候怎么恢复
- [ ] view渲染
- [ ] singleTask启动模式
- [ ] 消息机制实现
- [ ] App启动崩溃异常捕捉
- [ ] ListView的优化
- [ ] Android进程分类
- [ ] 前台切换到后台，然后再回到前台，Activity生命周期回调方法。弹出Dialog，生命值周期回调方法。
- [ ] RecycleView的使用，原理，RecycleView优化
- [ ] ANR的原因
- [ ] Service的开启方式
- [ ] Activity与Service通信的方式
- [ ] Activity之间的通信方式

## 网络

- [ ] Https请求慢的解决办法，DNS，携带数据，直接访问IP
- [ ] TCP/UDP的区别
- [ ] https相关，如何验证证书的合法性，https中哪里用了对称加密，哪里用了非对称加密，对加密算法（如RSA）等是否有了解
- [ ] TCP与UDP区别与应用（三次握手和四次挥手）涉及到部分细节（如client如何确定自己发送的消息被server收到） HTTP相关 提到过Websocket 问了WebSocket相关以及与socket的区别
- [ ] 多线程断点续传原理

## 算法

- [ ] 排序，快速排序的实现
- [ ] 树：B树、B+树的介绍
- [ ] 图：有向无环图的解释
- [ ] 二叉树 深度遍历与广度遍历
- [ ] 常用数据结构简介
- [ ] 判断环（猜测应该是链表环）
- [ ] 排序，堆排序实现
- [ ] 链表反转
- [ ] x个苹果，一天只能吃一个、两个、或者三个，问多少天可以吃完
- [ ] 堆排序过程，时间复杂度，空间复杂度
- [ ] 快速排序的时间复杂度，空间复杂度
- [ ] 翻转一个单项链表
- [ ] 两个不重复的数组集合中，求共同的元素
- [ ] 上一问扩展，海量数据，内存中放不下，怎么求出
- [ ] 合并多个单有序链表（假设都是递增的）
- [ ] 算法判断单链表成环与否？
- [ ] 二叉树，给出根节点和目标节点，找出从根节点到目标节点的路径
- [ ] 一个无序，不重复数组，输出N个元素，使得N个元素的和相加为M，给出时间复杂度、空间复杂度。手写算法
- [ ] 数据结构中堆的概念，堆排序

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

## 设计模式

- [ ] 设计模式相关（例如Android中哪里使用了观察者模式，单例模式相关）
- [ ] MVP模式
- [ ] Java设计模式，观察者模式
- [ ] 模式MVP，MVC介绍

参考：

1. [知乎Misssss Cathy的回答](https://zhuanlan.zhihu.com/p/30016683)

