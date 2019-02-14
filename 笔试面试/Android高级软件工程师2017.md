# Android 面试题总结

## 1、Java & Kotlin & 一些常用 API

### 1.1 LruCache 的原理

LruCache 用来实现基于内存的缓存，LRU 就是**最近最少使用**的意思，LruCache 基于 **LinkedHashMap** 实现。LinkedHashMap 在 HashMap 之上进行了封装，除了具有哈希功能，还将数据插入到双向链表中维护。每次获取到的数据会被移动到链表的尾部，当达到了缓存的最大的数量的时候就将链表首部的记录移出。

资料：[《Android 内存缓存框架 LruCache 的源码分析》](https://juejin.im/post/5bea581be51d451402494af2)

关联：

#### 1.1.1 DiskLruCache

#### 1.2.1 Glide 缓存的实现原理

### 1.2 SparseArray 的原理

SparseArray 主要用来替换 Java 中的 HashMap，因为 HashMap 将整数类型的键默认装箱成 Integer (效率比较低). 而 SparseArray **通过内部维护两个数组来进行映射**，并且使用**二分查找**寻找指定的键，所以**它的键对应的数组无需是包装类型**。SparseArray 用于当 HashMap 的键是 Integer 的情况，它会在内部维护一个 int 类型的数组来存储键。同理，还有 LongSparseArray, BooleanSparseArray 等，都是用来通过减少装箱操作来节省内存空间的。但是，因为它内部使用二分查找寻找键，所以其效率不如 HashMap 高，所以当要存储的键值对的数量比较大的时候，考虑使用 HashMap. 

### 1.3 对 Java 注解的理解

Java 注解在 Android 中主要有两种使用方式：

1. 基于**反射**：这种可以用来实现一些注解的解析等简单的工作。通过获取类及其字段、方法等的注解信息来获取类的信息。因为反射本身的性能问题，所以应当尽量少得使用。
2. 基于 **AnnotationProcessor** 的：也就是在编译期间动态生成一堆代码，用来实现业务逻辑，通常也会对我们的业务类进行代理，然后在运行时通过反射调用我们的业务类，可以用来对类的功能进行增强等。使用这种方式的注解时可能会用到 **Javapoet** 来帮助我们生成 Java 文件。

更多：[《Java 注解及其在 Android 中的应用》](https://juejin.im/post/5b824b8751882542f105447d).

关联：ButterKnife, ARouter

### 1.4 ArrayList 与 LinkedList 区别

ArrayList：**基于动态数组，底层使用 System.arrayCopy() 实现数组扩容；查找值的复杂度为 O(1)，增删的时候可能扩容，所以比 LinkedList 高；如果能够大概估出列表的长度，可以通过在 new 出实例的时候指定一个大小来指定数组的初始大小，以减少扩容的次数；适合应用到查找多于增删的情形，比如作为 Adapter 的数据的容器**。

LinkedList：**基于双向链表；增删的复杂度为 O(1)，查找的复杂度为 O(n)；适合应用到增删比较多的情形**。

相关：

#### 1.4.1 如何实现线程间安全地操作 List？

可以根据具体的业务进行选择：

1. 使用 `sychronized` 在操作 List 的时候进行控制；
2. 使用 `Collections.synchronizedList()` 进行包装，以实现线程安全；
3. 读多写少的情况下，为了提升效率还可以使用 `CopyOnWriteArrayList` 代替 ArrayList，使用 `ConcurrentLinkedQueue` 代替 LinkedList. 

方法 2 中的方式本质是对传入的 List 做了一层代理，每个方法进行了线程安全的包装，通过对一个局部变量进行加锁 (`sychronized`) 实现的；方法 3 中类似于 ArrayList，但是读操作的时候不加锁，写的时候会加锁，加锁是用 `ReentrantLock` 实现的。

### 1.5 Object 类的 equal() 和 hashcode() 方法重写？

两个**都具有决定一个对象身份功能，所以两者的行为必须一致，覆写这两个方法需要遵循一定的原则**，可以通过参考《Effect Java》一书的相关章节详细了解。一般，我们不会覆写该方法，可以通过 IDEA 的工具生成该方法。可以从业务的角度考虑如何标志一个对象的唯一特征，或者使用它的全部字段来进行计算。

相关：

#### 1.5.1 Object 都有哪些方法？

`wait()`, `notify()`, `clone()`, `finilize()`,`toString()`, `equal()` 和 `hashCode()`. 

### 1.6 字符串：StringBuffer 与 StringBuilder 的区别？

前者是线程安全的，每个方法上面都使用 synchronized 关键字进行了加锁，后者是非线程安全的。一般情况下使用 StringBuilder 即可，因为非多线程环境进行加锁是一种没有必要的开销。

#### 1.5.2 String 为什么要设计成不可变的？
#### 1.5.3 对Java中String的了解
#### 1.5.4 string to integer

### 1.7 ThreadLocal

ThreadLocal 通过将每个线程自己的局部变量存在自己的内部来实现线程安全。使用它的时候会定义它的静态变量，每个线程看似是从 TL 中获取数据，而实际上 TL 只起到了键值对的键的作用，实际的数据会以哈希表的形式存储在 Thread 实例的 Map 类型局部变量中。当调用 TL 的 `get()` 方法的时候会使用 `Thread.currentThread()` 获取当前 Thread 实例，然后从该实例的 Map 局部变量中，使用 TL 作为键来获取存储的值。

其他，Thread 内部的 Map 使用线性数组解决哈希冲突。

资料：[《ThreadLocal的使用及其源码实现》](https://juejin.im/post/5b44cd7c6fb9a04f980cb065)

### 1.8 Map 相关：HashMap、ConcurrentHashMap 以及 HashTable

HashMap (下称 HM) 是哈希表，ConcurrentHashMap (下称 CHM) 也是哈希表，它们之间的区别是 HM 不是线程安全的，CHM 线程安全，并且对锁进行了优化。对应 HM 的还有 HashTable (下称 HT)，它通过对内部的每个方法加锁来实现线程安全，效率较低。

## TODO

#### 1.8.1 集合 Set 实现 Hash 怎么防止碰撞

HashSet 内部通过 HashMap 实现，HashMap 解决碰撞使用的是拉链法，碰撞的元素会放进链表中，链表过长的话会转换成红黑树。



### 1.9 锁：synchronized 与 Lock (重入锁) 的区别

1. **等待可中断**：当持有锁的线程长期不释放锁的时候，正在等待的线程可以选择放弃等待；（两种方式获取锁的时候都会使计数+1，但是方式不同，所以重入锁可以终端）
2. **公平锁**：当多个线程等待同一个锁时，公平锁会按照申请锁的时间顺序来依次获得锁；而非公平锁，当锁被释放时任何在等待的线程都可以获得锁（不论时间尝试获取的时间先后）。sychronized 只支持非公平锁，Lock 可以通过构造方法指定使用公平锁还是非公平锁。
3. **锁可以绑定多个条件**：ReentrantLock 可以绑定多个 Condition 对象，而 sychronized 要与多个条件关联就不得不加一个锁，ReentrantLock 只要多次调用newCondition 即可。

#### 1.9.1 死锁，线程死锁的4个条件？

1. 互斥：某种资源一次只允许一个进程访问，即该资源一旦分配给某个进程，其他进程就不能再访问，直到该进程访问结束。（一个筷子只能被一个人拿）
2. 占有且等待：一个进程本身占有资源（一种或多种），同时还有资源未得到满足，正在等待其他进程释放该资源。（每个人拿了一个筷子还要等其他人放弃筷子）
3. 不可抢占：别人已经占有了某项资源，你不能因为自己也需要该资源，就去把别人的资源抢过来。（别人手里的筷子你不能去抢）
4. 循环等待：存在一个进程链，使得每个进程都占有下一个进程所需的至少一种资源。（每个人都在等相邻的下一个人放弃自己的筷子）

#### 1.9.2 synchronized 的原理

在所修饰的方法或者方法块周围添加 monitorenter 和 monitorexit 指令，进入方法的时候需要先获取类的实例或者类的监视器。每被获取一次会加 1，以此来统计获取的次数。离开方法的时候减 1，当计数减为 0 的时候，锁被释放。

#### 1.9.3 CAS 介绍

#### 1.9.4 Lock 原理

#### 1.9.5 volatile 原理和用法

### 2.0 线程：线程的状态

#### 2.0.1 开启线程的三种方式，run() 和 start() 方法区别

1. Thread 覆写 `run()` 方法；
2. Thread + Runnable；
3. ExectorService + Callable；

`start()` 会调用 native 的 `start()` 方法，然后 `run()` 方法会被回调，此时 `run()` 异步执行；如果直接调用 `run()`，它会使用默认的实现（除非覆写了），并且会在当前线程中执行，此时 Thread 如同一个普通的类。

#### 

### 2.1 并发类：并发集合了解哪些

1. ConcurrentHashMap：线程安全的 HashMap，对桶进行加锁，降低锁粒度提升性能。
2. ConcurrentSkipListMap：跳表，自行了解，给跪了……
3. ConCurrentSkipListSet：借助 ConcurrentSkipListMap 实现
4. CopyOnWriteArrayList：读多写少的 ArrayList，写的时候加锁
5. CopyOnWriteArraySet：借助 CopyOnWriteArrayList 实现的……
6. ConcurrentLinkedQueue：无界且线程安全的 Queue，其 `poll()` 和 `add()` 等方法借助 CAS 思想实现。锁比较轻量。


- [ ] Java线程池
- [ ] 多线程（关于AsyncTask缺陷引发的思考）
- [ ] AsyncTask机制，如何取消AsyncTask
- [ ] 手写生产者/消费者模式
- [ ] 如何实现线程同步？
- [ ] hashmap如何put数据（从hashmap源码角度讲解）？
- [ ] List 和 Map 的实现方式以及存储方式。
- [ ] 静态内部类的设计意图
- [ ] 线程如何关闭，以及如何防止线程的内存泄漏
- [ ] NIO
- [ ] List,Set,Map的区别
- [ ] HashSet与HashMap怎么判断集合元素重复
- [ ] wait/notify
- [ ] 多线程：怎么用、有什么问题要注意；Android线程有没有上限，然后提到线程池的上限
- [ ] Java中同步使用的关键字，死锁
- [ ] HashMap的实现，与HashSet的区别
- [ ] 死锁的概念，怎么避免死锁
- [ ] 内部类和静态内部类和匿名内部类，以及项目中的应用
- [ ] ReentrantLock的内部实现
- [ ] 集合的接口和具体实现类，介绍
- [ ] TreeMap具体实现

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
- [ ] listview 图片加载错乱的原理和解决方案
- [ ] service 生命周期
- [ ] handler 实现机制（很多细节需要关注：如线程如何建立和退出消息循环等等）
- [ ] 数据库数据迁移问题
- [ ] 是否熟悉Android jni开发，jni如何调用java层代码
- [ ] 进程间通信的方式
- [ ] 计算一个 view 的嵌套层级
- [ ] 项目组件化的理解
- [ ] Android 系统为什么会设计ContentProvider，进程共享和线程安全问题
- [ ] Android 相关优化（如内存优化、网络优化、布局优化、电量优化、业务优化） 
- [ ] EventBus 实现原理
- [ ] 四大组件
- [ ] Android中数据存储方式
- [ ] ActicityThread相关？
- [ ] Android中进程内存的分配，能不能自己分配定额内存
- [ ] 序列化，Android为什么引入 Parcelable
- [x] 有没有尝试简化 Parcelable 的使用

    通常有两种解决方案，一种是反射，但是性能低；一种是注解，动态生成代码。

- [x] ViewPager 使用细节，如何设置成每次只初始化当前的 Fragment，其他的不初始化

    不能使用 ViewPager.setOffscreenPageLimit(0)，其最小值为1. 

- [ ] ListView 重用的是什么
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
- [x] LinearLayout、RelativeLayout、FrameLayout 的特性、使用场景

    ...

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
- [ ] Oom 是否可以 try catch ？

内存优化    


- [ ] 内存泄露如何产生？
- [ ] 适配器模式，装饰者模式，外观模式的异同？
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
- [ ] ANR 的原因



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

1. 整理自：[知乎 Misssss Cathy 的回答](https://zhuanlan.zhihu.com/p/30016683)

