# Android 高级面试-6：性能优化

## 1、内存优化

### 1.1 OOM

**问题：OOM 的几种常见情形？**

1. `数据太大`：比如加载图片太大，原始的图片没有经过采样，完全加载到内存中导致内存爆掉。
2. `内存泄漏`
3. `内存抖动`：内存抖动是指内存频繁地分配和回收，而`频繁的 GC `会导致卡顿，严重时还会导致 OOM。一个很经典的案例是 String 拼接时创建大量小的对象。此时由于大量小对象频繁创建，导致内存不连续，无法分配大块内存，系统直接就返回 OOM 了。

**问题：OOM 是否可以 Try Catch ？**

Catch 是可以 Catch 到的，但是这样不符合规范，Error 说明程序中发生了错误，我们应该使用引用四种引用、增加内存或者减少内存占用来解决这个问题。

### 1.2 内存泄漏

**问题：常见的内存泄漏的情形，以及内存泄漏应该如何分析？**

1. `单例` 引用了 Activity 的 Context，可以使用 `Context.getApplicationContext()` 获取整个应用的 Context 来使用；
2. `静态变量` 持有 Activity 的引用，原因和上面的情况一样，比如为了避免反复创建一个内部实例的时候使用静态的变量；
3. `非静态内部类` 导致内存泄露，典型的有：
    1. Handler：Handler 默认持有外部 Activity 的引用，发送给它的 Message 持有 Handler 的引用，Message 会被放入 MQ 中，因此可能会造成泄漏。解决方式是使用弱引用来持有外部 Activity 的引用。另一种方式是在 Activity 的 `onDestroy()` 方法中调用 `mHandler.removeCallbacksAndMessages(null)` 从 MQ 中移除消息。 后者更好一些！因为它移除了 Message. 
    2. 另一种情形是使用非静态的 Thread 或者 AsyncTask，因为它们持有 Activity 的引用，解决方式是使用 `静态内部类+弱引用`。
4. `广播`：未取消注册广播。在 Activity 中注册广播，如果在 Activity 销毁后不取消注册，那么这个刚播会一直存在系统中，同上面所说的非静态内部类一样持有 Activity 引用，导致内存泄露。
5. `资源`：未关闭或释放导致内存泄露。使用 IO、File 流或者 Sqlite、Cursor 等资源时要及时关闭。这些资源在进行读写操作时通常都使用了缓冲，如果及时不关闭，这些缓冲对象就会一直被占用而得不到释放，以致发生内存泄露。
6. `属性动画`：在 Activity 中启动了属性动画（ObjectAnimator），但是在销毁的时候，没有调用 cancle 方法，虽然我们看不到动画了，但是这个动画依然会不断地播放下去，动画引用所在的控件，所在的控件引用 Activity，这就造成 Activity 无法正常释放。因此同样要在 Activity 销毁的时候 cancel 掉属性动画，避免发生内存泄漏。
7. `WebView`：WebView 在加载网页后会长期占用内存而不能被释放，因此我们在 Activity 销毁后要调用它的 destory() 方法来销毁它以释放内存。

### 1.3 内存优化相关的工具

1. `检查内存泄漏`：Square 公司开源的用于检测内存泄漏的库，[LeakCanary](https://github.com/square/leakcanary).
2. `Memory Monitor`：AS 自带的工具，可以用来主动触发 GC，获取堆内存快照文件以便进一步进行分析（通过叫做 Allocation Tracker 的工具获取快照）。（属于开发阶段使用的工具，开发时应该多使用它来检查内存占用。）
3. `Device Monitor`：包含多种分析工具：线程，堆，网络，文件等（位于 sdk 下面的 tools 文件夹中）。可以通过这里的 Heap 选项卡的 Cause GC 按钮主动触发 GC，通过内存回收的状态判断是否发生了内存泄漏。
4. `MAT`：首先通过 DDMS 的 Devices 选项卡下面的 Dump HPROF File 生成 hrpof 文件，然后用 SDK 的 hprof-conv 将该文件转成标准 hprof 文件，导入 MAT 中进行分析。 

## 3、ANR

**问题：ANR 的原因**    
**问题：ANR 怎么分析解决**

满足下面的一种情况系统就会弹出 ANR 提示

1. `输入事件 (按键和触摸事件) 5s` 内没被处理；
2. BroadcastReceiver 的事件 ( onRecieve() 方法) 在规定时间内没处理完 (`前台广播为 10s，后台广播为 60s`)；
3. Service `前台 20s 后台 200s` 未完成启动；
4. ContentProvider 的 `publish() 在 10s` 内没进行完。

最终弹出 ANR 对话框的位置是与 AMS 同目录的类 `AppErrors 的 handleShowAnrUi()` 方法。最初抛出 ANR 是在 InputDispatcher.cpp 中。后回在上述方法调用 AMS 的 inputDispatchingTimedOut() 方法继续处理，并最终在 inputDispatchingTimedOut() 方法中将事件传递给 AppErrors。

解决方式:

1. 使用 adb 导出 ANR 日志并进行分析，发生 ANR的时候系统会记录 ANR 的信息并将其存储到 /data/anr/traces.txt 文件中（在比较新的系统中会被存储都 /data/anr/anr_* 文件中）。或者在开发者模式中选择将日志导出到 sdcard 之后再从 sdcard 将日志发送到电脑端进行查看
2. 使用 DDMS 的 `traceview` 进行分析：到 SDK 安装目录的 tools 目录下面使用 monitor.bat 打开 DDMS。使用 TraceView 来通过耗时方法调用的信息定位耗时操作的位置。
3. 使用开源项目 `ANR-WatchDog` 来检測 ANR：创建一个检测线程，该线程不断往 UI 线程 post 一个任务，然后睡眠固定时间，等该线程又一次起来后检測之前 post 的任务是否运行了，假设任务未被运行，则生成 ANRError，并终止进程。

常见的 ANR 场景：

1. `I/O 阻塞`
2. `网络阻塞`
3. `多线程死锁`
4. `由于响应式编程等导致的方法死循环`
5. `由于某个业务逻辑执行的时间太长`

避免 ANR 的方法：

1. UI 线程尽量只做跟 UI 相关的工作；
2. 耗时的工作 (比如数据库操作，I/O，网络操作等)，采用`单独的工作线程`处理；
3. 用 `Handler` 来处理 UI 线程和工作线程的交互；
4. 使用 `RxJava` 等来处理异步消息。

## 4、性能调优工具

## 5、优化经验

### 5.1 优化经验

虽然一直强调优化，但是许多优化应该是在开发阶段就完成的，程序逻辑的设计可能会影响程序的性能。如果开发完毕之后再去考虑对程序的逻辑进行优化，那么阻力会比较大。因此，编程的时候应该养成好的编码习惯，同时注意收集性能优化的经验，在开发的时候进行避免。

代码质量检查工具：

1. 使用 `SonarLint` 来对代码进行静态检查，使代码更加符合规范；
2. 使用`阿里的 IDEA 插件`对 Java 的代码质量进行检查；

在 Android4.4 以上的系统上，对于 Bitmap 的解码，decodeStream() 的效率要高于 decodeFile() 和 decodeResource(), 而且高的不是一点。所以解码 Bitmap 要使用 decodeStream()，同时传给 decodeStream() 的文件流是 BufferedInputStream：

```kotlin
val bis =  BufferedInputStream(FileInputStream(filePath))
val bitmap = BitmapFactory.decodeStream(bis,null,ops)
```

Java 相关的优化：

1. `静态优于抽象`：如果你并不需要访问一个对系那个中的某些字段，只是想调用它的某些方法来去完成一项通用的功能，那么可以将这个方法设置成静态方法，调用速度提升 15%-20%，同时也不用为了调用这个方法去专门创建对象了，也不用担心调用这个方法后是否会改变对象的状态(静态方法无法访问非静态字段)。

2. `多使用系统封装好的 API`：系统提供不了的 Api 完成不了我们需要的功能才应该自己去写，因为使用系统的 Api 很多时候比我们自己写的代码要快得多，它们的很多功能都是通过底层的汇编模式执行的。举个例子，实现数组拷贝的功能，使用循环的方式来对数组中的每一个元素一一进行赋值当然可行，但是直接使用系统中提供的 `System.arraycopy()` 方法会让执行效率快 9 倍以上。

3. `避免在内部调用 Getters/Setters 方法`：面向对象中封装的思想是不要把类内部的字段暴露给外部，而是提供特定的方法来允许外部操作相应类的内部字段。但在 Android 中，字段搜寻比方法调用效率高得多，我们直接访问某个字段可能要比通过 getters 方法来去访问这个字段快 3 到 7 倍。但是编写代码还是要按照面向对象思维的，我们应该在能优化的地方进行优化，比如避免在内部调用 getters/setters 方法。

4. `使用 static final 修饰常量`：因为常量会在 dex 文件的初始化器当中进行初始化。当我们调用 intVal 时可以直接指向 42 的值，而调用 strVal 会用一种相对轻量级的字符串常量方式，而不是字段搜寻的方式。这种优化方式只对基本数据类型以及 String 类型的常量有效，对于其他数据类型的常量无效。

5. `合理使用数据结构`：比如 `android.util` 下面的 `Pair<F, S>`，在希望某个方法返回的数据恰好是两个的时候可以使用。显然，这种返回方式比返回数组或者列表含义清晰得多。延申一下：`有时候合理使用数据结构或者使用自定义数据结构，能够起到化腐朽为神奇的作用`。

6. `多线程`：不要开太多线程，如果小任务很多建议使用线程池或者 AsyncTask，建议直接使用 RxJava 来实现多线程，可读性和性能更好。

7. `合理选择数据结构`：根据具体应用场景选择 LinkedList 和 ArrayList，比如 Adapter 中查找比增删要多，因此建议选择 ArrayList. 

8. `合理设置 buffer`：在读一个文件我们一般会设置一个 buffer。即先把文件读到 buffer 中，然后再读取 buffer 的数据。所以: 真正对文件的次数 = 文件大小 / buffer大小 。 所以如果你的 buffer 比较小的话，那么读取文件的次数会非常多。当然在写文件时 buffer 是一样道理的。很多同学会喜欢设置 1KB 的 buffer，比如 byte buffer[] = new byte[1024]。如果要读取的文件有 20KB， 那么根据这个 buffer 的大小，这个文件要被读取 20 次才能读完。

9. ListView 复用，`getView()` 里尽量复用 conertView，同时因为 `getView()` 会频繁调用，要避免频繁地生成对象。

10. `谨慎使用多进程`，现在很多App都不是单进程，为了保活，或者提高稳定性都会进行一些进程拆分，而实际上即使是空进程也会占用内存(1M左右)，对于使用完的进程，服务都要及时进行回收。

11. 尽量使用系统资源，系统组件，图片甚至控件的 id.

12. `数据相关`：序列化数据使用 protobuf 可以比 xml 省 30% 内存，慎用 shareprefercnce，因为对于同一个 sp，会将整个 xml 文件载入内存，有时候为了读一个配置，就会将几百 k 的数据读进内存，数据库字段尽量精简，只读取所需字段。

13. `dex优化，代码优化，谨慎使用外部库`，有人觉得代码多少于内存没有关系，实际会有那么点关系，现在稍微大一点的项目动辄就是百万行代码以上，多 dex 也是常态，不仅占用 rom 空间，实际上运行的时候需要加载 dex 也是会占用内存的(几 M )，有时候为了使用一些库里的某个功能函数就引入了整个庞大的库，此时可以考虑抽取必要部分，开启 proguard 优化代码，使用 Facebook redex 使用优化 dex (好像有不少坑)。

常用的程序性能测试方法

1. `时间测试`：方式很简单只要在代码的上面和下面定义一个long型的变量，并赋值给当前的毫秒数即可。比如
    
    ```java
    long sMillis = System.currentTimeMillis();
    // ...代码块
    long eMillis = System.currentTimeMillis();
    ```
然后两者相减即可得到程序的运行时间。

2. `内存消耗测试`：获取代码块前后的内存，然后相减即可得到这段代码当中的内存消耗。获取当前内存的方式是

    ```java
    long total = Runtime.getRuntime().totalMemory(); // 获取系统中内存总数
    long free = Runtime.getRuntime().freeMemory(); // 获取剩余的内存总数
    long used = total - free; // 使用的内存数
    ```

在使用的时候只要在代码块的两端调用 `Runtime.getRuntime().freeMemory()` 然后再相减即可得到使用的内存总数。

### 5.2 布局优化

1. 在选择使用 Android 中的布局方式的时候应该遵循：尽量少使用性能比较低的容器控件,比如 RelativeLayout，但如果使用 RelativeLayout 可以降低布局的层次的时候可以考虑使用。
2. 使用 `<include>` 标签复用布局：多个地方共用的布局可以使用 `<include>` 标签在各个布局中复用；
3. 可以通过使用 `<merge>` 来降低布局的层次。 `<merge>` 标签通常与 `<include>` 标签一起使用， `<merge>` 作为可以复用的布局的根控件。然后使用 `<include>` 标签引用该布局。
4. 使用 `<ViewStub>` 标签动态加载布局：`<ViewStub>` 标签可以用来在程序运行的时候决定加载哪个布局，而不是一次性全部加载。
5. 性能分析：使用 `Android Lint` 来分析布局；
6. 性能分析：避免过度绘制，在手机的开发者选项中的绘图选项中选择显示布局边界来查看布局
7. 性能分析：`Hierarchy View`，可以通过 Hierarchy View 来获取当前的 View 的层次图
8. 使用 `ConstaintLayout`：用来降低布局层次；
9. 性能分析：使用 `systrace` 分析 UI 性能；
10. onDraw() 方法会被频繁调用，因此不应该在其中做耗时逻辑和声明对象

### 5.3 内存优化

1. `防止内存泄漏`：见内存泄漏；

2. `使用优化过的集合`；

3. `使用优化过的数据集合`：如 `SparseArray`、`SparseBooleanArray`等来替换 HashMap。因为 HashMap 的键必须是对象，而对象比数值类型需要多占用非常多的空间。

4. `少使用枚举`：枚举可以合理组织数据结构，但是枚举是对象，比普通的数值类型需要多使用很多空间。

5. `当内存紧张时释放内存`：`onTrimMemory()` 方法还有很多种其他类型的回调，可以在手机内存降低的时候及时通知我们，我们应该根据回调中传入的级别来去决定如何释放应用程序的资源。

6. 读取一个 Bitmap 图片的时候，不要去加载不需要的分辨率。可以压缩图片等操作，使用性能稳定的图片加载框架，比如 Glide.

7. `谨慎使用抽象编程`：在 Android 使用抽象编程会带来额外的内存开支，因为抽象的编程方法需要编写额外的代码，虽然这些代码根本执行不到，但是也要映射到内存中，不仅占用了更多的内存，在执行效率上也会有所降低。所以需要合理的使用抽象编程。

8. `尽量避免使用依赖注入框架`：使用依赖注入框架貌似看上去把 findViewById() 这一类的繁琐操作去掉了，但是这些框架为了要搜寻代码中的注解，通常都需要经历较长的初始化过程，并且将一些你用不到的对象也一并加载到内存中。这些用不到的对象会一直站用着内存空间，可能很久之后才会得到释放，所以可能多敲几行代码是更好的选择。

9. `使用多个进程`：谨慎使用，多数应用程序不该在多个进程中运行的，一旦使用不当，它甚至会增加额外的内存而不是帮我们节省内存。这个技巧比较适用于哪些需要在后台去完成一项独立的任务，和前台是完全可以区分开的场景。比如音乐播放，关闭软件，已经完全由 Service 来控制音乐播放了，系统仍然会将许多 UI 方面的内存进行保留。在这种场景下就非常适合使用两个进程，一个用于 UI 展示，另一个用于在后台持续的播放音乐。关于实现多进程，只需要在 Manifast 文件的应用程序组件声明一个`android:process` 属性就可以了。进程名可以自定义，但是之前要加个冒号，表示该进程是一个当前应用程序的私有进程。

10. `分析内存的使用情况`：系统不可能将所有的内存都分配给我们的应用程序，每个程序都会有可使用的内存上限，被称为堆大小。不同的手机堆大小不同，如下代码可以获得堆大小 `int heapSize = AMS.getMemoryClass()` 结果以 MB 为单位进行返回，我们开发时应用程序的内存不能超过这个限制，否则会出现 OOM。

11. `节制的使用 Service`：如果应用程序需要使用 Service 来执行后台任务的话，只有当任务正在执行的时候才应该让 Service 运行起来。当启动一个 Service 时，系统会倾向于将这个 Service 所依赖的进程进行保留，系统可以在 LRUcache 当中缓存的进程数量也会减少，导致切换程序的时候耗费更多性能。我们可以使用 IntentService，当后台任务执行结束后会自动停止，避免了 Service 的内存泄漏。

12. 字符串优化：[Android 性能优化之String篇](https://blog.csdn.net/vfush/article/details/53038437)

### 5.4 异常崩溃 & 稳定性

**问题：如何保持应用的稳定性**
**问题：App 启动崩溃异常捕捉**

1. 使用热补丁
2. 自己写代码捕获异常
3. 使用异常收集工具
4. 开发就是测试，自己的逻辑自己先测一边

### 5.5 优化工具

**问题：性能优化如何分析 systrace？**

下面将简单介绍几个主流的辅助分析内存优化的工具，分别是 

1. MAT (Memory Analysis Tools)
2. Heap Viewer
3. Allocation Tracker
4. Android Studio 的 Memory Monitor
5. LeakCanary

https://www.jianshu.com/p/0df5ad0d2e6a

MAT (Memory Analysis Tools)，作用：查看当前内存占用情况。通过分析 Java 进程的内存快照 HPROF 分析，快速计算出在内存中对象占用的大小，查看哪些对象不能被垃圾收集器回收 & 可通过视图直观地查看可能造成这种结果的对象

- [MAT - Memory Analyzer Tool 使用进阶](http://www.lightskystreet.com/2015/09/01/mat_usage/)    
- [MAT使用教程](https://blog.csdn.net/itomge/article/details/48719527)

Heap Viewer，定义：一个的 Java Heap 内存分析工具。作用：查看当前内存快照。可查看分别有哪些类型的数据在堆内存总以及各种类型数据的占比情况。

### 5.6 启动优化

**问题：能优化，怎么保证应用启动不卡顿**
**问题：统计启动时长,标准**

1. 方式 1：使用 ADB：获取启动速度的第一种方式是使用 ADB，使用下面的指令的时候在启动应用的时候会使用 AMS 进行统计。但是缺点是统计时间不够准确：`adb shell am start -n ｛包名｝/｛包名｝.{活动名}`
2. 方式 2：代码埋点：在 Application 的 attachBaseContext() 方法中记录开始时间，第一个 Activity 的 onWindowFocusChanged() 中记录结束时间。缺点是统计不完全，因为在 attachBaseContext() 之前还有许多操作。
3. 方式 3：TraceView：在 AS 中打开 DDMS，或者到 SDK 安装目录的 tools 目录下面使用 monitor.bat 打开 DDMS。通过 TraceView 主要可以得到两种数据：单次执行耗时的方法以及执行次数多的方法。但 TraceView 性能耗损太大，不能比较正确反映真实情况。
4. 方式 4：Systrace：Systrace 能够追踪关键系统调用的耗时情况，如系统的 IO 操作、内核工作队列、CPU 负载、Surface 渲染、GC 事件以及 Android 各个子系统的运行状况等。但是不支持应用程序代码的耗时分析。
5. 方式 5：Systrace + 插桩：类似于 AOP，通过切面为每个函数统计执行时间。这种方式的好处是能够准确统计各个方法的耗时。`TraceMethod.i(); /* do something*/ TraceMethod.o();`
6. 方式 6：录屏：录屏方式收集到的时间，更接近于用户的真实体感。可以在录屏之后按帧来进行统计分析。

启动优化

1. `延迟初始化`：一些逻辑，如果没必要在程序启动的时候就立即初始化，那么可以将其推迟到需要的时候再初始化。比如，我们可以使用单例的方式来获取类的实例，然后在获取实例的时候再进行初始化操作。`但是需要注意的是，懒加载要防止集中化，否则容易出现首页显示后用户无法操作的情形。可以按照耗时和是否必要将业务划分到四个维度：必要且耗时，必要不耗时，非必要但耗时，非必要不耗时。` 然后对应不同的维度来决定是否有必要在程序启动的时候立即初始化。
2. `防止主线程阻塞`：一般我们也不会把耗时操作放在主线程里面，毕竟现在有了 RxJava 之后，在程序中使用异步代价并不高。这种耗时操作包括，大量的计算、IO、数据库查询和网络访问等。另外，关于开启线程池的问题下面的话总结得比较好，除了一般意义上线程池和使用普通线程的区别，还要考虑应用启动这个时刻的特殊性，特定场景下单个时间点的表现 Thread 会比 ThreadPoolExecutor 好：同样的创建对象，ThreadPoolExecutor 的开销明显比 Thread 大。
3. `布局优化`：如，之前我在使用 Fragment 和 ViewPager 搭配的时候，发现虽然 Fragment 可以被复用，但是如果通过 Adapter 为 ViewPager 的每个项目指定了标题，那么这些标题控件不会被复用。当 ViewPager 的条目比较多的时候，甚至会造成 ANR.
4. `使用启动页面防止白屏`：这种方法只是治标不治本的方法，就是在应用启动的时候避免白屏，可以通过设置自定义主题来实现。

其他借鉴办法

1. 使用 BlockCanary 检测卡顿：它的原理是对 Looper 中的 loop() 方法打处的日志进行处理，通过一个自定义的日志输出 Printer 监听方法执行的开始和结束。（更加详细的源码分析参考这篇文章：[Android UI卡顿监测框架BlockCanary原理分析](https://www.jianshu.com/p/e58992439793)）
2. GC 优化：减少垃圾回收的时间间隔，所以在启动的过程中不要频繁创建对象，特别是大对象，避免进行大量的字符串操作，特别是序列化跟反序列化过程。一些频繁创建的对象，例如网络库和图片库中的 Byte 数组、Buffer 可以复用。如果一些模块实在需要频繁创建对象，可以考虑移到 Native 实现。
3. 类重排：如果我们的代码在打包的时候被放进了不同的 dex 里面，当启动的时候，如果需要用到的类分散在各个 dex 里面，那么系统要花额外的时间到各个 dex 里加载类。因此，我们可以通过类重排调整类在 Dex 中的排列顺序，把启动时用到的类放进主 dex 里。目前可以使用 ReDex 的 Interdex 调整类在 Dex 中的排列顺序。
4. 资源文件重排：这种方案的原理时先通过测试找出程序启动过程中需要加载的资源，然后再打包的时候通过修改 7z 压缩工具将上述热点资源放在一起。这样，在系统进行资源加载的时候，这些资源将要用到的资源会一起被加载进程内存当中并缓存，减少了 IO 的次数，同时不需要从磁盘读取文件，来提高应用启动的速度。

### 5.7 网络优化

1. Network Monitor: Android Studio 内置的 Monitor工具中就有一个 Network Monitor;
2. 抓包工具：Wireshark, Fiddler, Charlesr 等抓包工具，Android 上面的无 root 抓包工具；
3. Stetho：Android 应用的调试工具。无需 Root 即可通过 Chrome，在 Chrome Developer Tools 中可视化查看应用布局，网络请求，SQLite，preference 等。
4. Gzip 压缩：使用 Gzip 来压缩 request 和 response, 减少传输数据量, 从而减少流量消耗.
5. 数据交换格式：JSON 而不是 XML，另外 Protocol Buffer 是 Google 推出的一种数据交换格式.
6. 图片的 Size：使用 WebP 图片，修改图片大小；
7. 弱网优化
    1. 界面先反馈, 请求延迟提交例如, 用户点赞操作, 可以直接给出界面的点赞成功的反馈, 使用JobScheduler在网络情况较好的时候打包请求.
    2. 利用缓存减少网络传输；
    3. 针对弱网(移动网络), 不自动加载图片
    4. 比方说 Splash 闪屏广告图片, 我们可以在连接到 Wifi 时下载缓存到本地; 新闻类的 App 可以在充电, Wifi 状态下做离线缓存
8. IP 直连与 HttpDns：DNS 解析的失败率占联网失败中很大一种，而且首次域名解析一般需要几百毫秒。针对此，我们可以不用域名，才用 IP 直连省去 DNS 解析过程，节省这部分时间。HttpDNS 基于 Http 协议的域名解析，替代了基于 DNS 协议向运营商 Local DNS 发起解析请求的传统方式，可以避免 Local DNS 造成的域名劫持和跨网访问问题，解决域名解析异常带来的困扰。
9. 请求频率优化：可以通过把网络数据保存在本地来实现这个需求，缓存数据，并且把发出的请求添加到队列中，当网络恢复的时候再及时发出。
10. 缓存：App 应该缓存从网络上获取的内容，在发起持续的请求之前，app 应该先显示本地的缓存数据。这确保了 app 不管设备有没有网络连接或者是很慢或者是不可靠的网络，都能够为用户提供服务。

### 5.8 电量优化

### 5.9 RV 优化

1. 数据处理和视图加载分离：从远端拉取数据肯定是要放在异步的，在我们拉取下来数据之后可能就匆匆把数据丢给了 VH 处理，其实，数据的处理逻辑我们也应该放在异步处理，这样 Adapter 在 notify change 后，ViewHolder 就可以简单无压力地做数据与视图的绑定逻辑。比如：`mTextView.setText(Html.fromHtml(data).toString());`这里的 Html.fromHtml(data) 方法可能就是比较耗时的，存在多个 TextView 的话耗时会更为严重，而如果把这一步与网络异步线程放在一起，站在用户角度，最多就是网络刷新时间稍长一点。
2. 数据优化：页拉取远端数据，对拉取下来的远端数据进行缓存，提升二次加载速度；对于新增或者删除数据通过 DiffUtil 来进行局部刷新数据，而不是一味地全局刷新数据。
3. 减少过渡绘制：减少布局层级，可以考虑使用自定义 View 来减少层级，或者更合理地设置布局来减少层级，不推荐在 RecyclerView 中使用 ConstraintLayout，有很多开发者已经反映了使用它效果更差。
4. 减少 xml 文件 inflate 时间：xml 文件 inflate 出 ItemView 是通过耗时的 IO 操作，尤其当 Item 的复用几率很低的情况下，随着 Type 的增多，这种 inflate 带来的损耗是相当大的，此时我们可以用代码去生成布局，即 new View() 的方式。
5. 如果 Item 高度是固定的话，可以使用 RecyclerView.setHasFixedSize(true); 来避免 requestLayout 浪费资源；
6. 如果不要求动画，可以通过 `((SimpleItemAnimator) rv.getItemAnimator()).setSupportsChangeAnimations(false);` 把默认动画关闭来提升效率。
7. 对 TextView 使用 String.toUpperCase 来替代 `android:textAllCaps="true"`；
8. 通过重写 RecyclerView.onViewRecycled(holder) 来回收资源。
9. 通过 RecycleView.setItemViewCacheSize(size); 来加大 RecyclerView 的缓存，用空间换时间来提高滚动的流畅性。
10. 如果多个 RecycledView 的 Adapter 是一样的，比如嵌套的 RecyclerView 中存在一样的 Adapter，可以通过设置 RecyclerView.setRecycledViewPool(pool); 来共用一个 RecycledViewPool。
11. 对 ItemView 设置监听器，不要对每个 Item 都调用 addXxListener，应该大家公用一个 XxListener，根据 ID 来进行不同的操作，优化了对象的频繁创建带来的资源消耗。

### 6.0 APK 优化

1. 开启混淆：哪些配置？
2. 资源混淆：AndRes
3. 只支持 armeabi-v7 架构的 so 库
4. 手动 Lint 检查，手动删除无用资源：删除没有必要的资源文件
5. 使用 Tnypng 等图片压缩工具对图片进行压缩
6. 大部分图片使用 Webp 格式代替：可以给UI提要求，让他们将图片资源设置为 Webp 格式，这样的话图片资源会小很多。如果对图片颜色通道要求不高，可以考虑转 jpg，最好用 webp，因为效果更佳。
7. 尽量不要在项目中使用帧动画
8. 使用 gradle 开启 `shrinkResources ture`：但有一个问题，就是图片 id 没有被引用的时候会被变成一个像素，所以需要在项目代码中引用所有表情图片的 id。
9. 减小 dex 的大小：
    1. 尽量减少第三方库的引用
    2. 避免使用枚举
    3. 避免重复功能的第三方库
10. 其他
    1. 用 7zip 代替压缩资源。
    2. 删除翻译资源，只保留中英文 
    3. 尝试将 `andorid support` 库彻底踢出你的项目。
    4. 尝试使用动态加载 so 库文件，插件化开发。
    5. 将大资源文件放到服务端，启动后自动下载使用。

## 6、相机优化

参考相机优化相关的内容。

### Bitmap 优化

https://blog.csdn.net/carson_ho/article/details/79549382

1. 使用完毕后 释放图片资源，优化方案： 
    1. 在 Android2.3.3（API 10）前，调用 Bitmap.recycle()方法 
    2. 在 Android2.3.3（API 10）后，采用软引用（SoftReference）

2. 根据分辨率适配 & 缩放图片

3. 按需 选择合适的解码方式

4. 设置 图片缓存

