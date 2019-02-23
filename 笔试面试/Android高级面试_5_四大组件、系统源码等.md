# Android 高级面试-5：四大组件、系统源码等

## 1、四大组件

### 1.1 Activity

- **Q：在两个 Activity 之间传递对象还需要注意什么呢？**

对象的大小。Intent 中的 Bundle 是使用 Binder 机制进行数据传送的。能使用的 Binder 的缓冲区是有大小限制的（有些手机是 2 M），而一个进程默认有 16 个 Binder 线程，所以一个线程能占用的缓冲区就更小了（有人以前做过测试，大约一个线程可以占用 128 KB）。所以当你看到 TransactionTooLargeException 异常时，你应该知道怎么解决了。

- **Q：onSaveInstanceState() 和 onRestoreInstanceState()**

当 Activity 被销毁的时候回调用 `onSaveInstanceState()` 方法来存储当前的状态。这样当 Activity 被重建的时候，可以在 `onCreate()` 和 `onRestoreInstanceState()` 中恢复状态。

对于 targetAPI 为 28 及以后的应用，该方法会在 `onStop()` 方法之后调用，对于之前的设备，这方法会在 `onStop()` 之前调用，但是无法确定是在 `onPause()` 之前还是之后调用。

`onRestoreInstanceState()` 方法用来恢复之前存储的状态，它会在 `onStart()` 和 `onPostCreate()` 之间被调用。此外，你也可以直接在 `onCreate()` 方法中进行恢复，但是基于这个方法调用的时机，如果有特别需求，可以在这个方法中进行处理。

- **Q：SingleTask 启动模式**
- **Q：Activity 启动模式**

1. **standard**：默认，每次启动的时候会创建一个新的实例，并且被创建的实例所在的栈与启动它的 Activity 是同一个栈。比如，A 启动了 B，那么 B 将会与 A 处在同一个栈。假如，我们使用 Application 的 Context 启动一个 Activity 的时候会抛出异常，这是因为新启动的 Activity 不知道自己将会处于哪个栈。可以在启动 Activity 的时候使用 `FLAG_ACTIVITY_NEW_TASK`。这样新启动的 Acitivyt 将会创建一个新的栈。
2. **singleTop**：栈顶复用，如果将要启动的 Activity 已经位于栈顶，那么将会复用栈顶的 Activity，并且会调用它的 `onNewIntent()`。常见的应用场景是从通知打开 Activity 时。
3. **singleTask**：单例，如果启动它的任务栈中存在该 Activity，那么将会复用该 Activity，并且会将栈内的、它之上的所有的 Activity 清理出去，以使得该 Activity 位于栈顶。常见的应用场景是启动页面、购物界面、确认订单界面和付款界面等。
4. **singleInstance**：这种启动模式会在启动的时候为其指定一个单独的栈来执行。如果用同样的intent 再次启动这个 Activity，那么这个 Activity 会被调到前台，并且会调用其 `onNewIntent()` 方法。

- **Q：下拉状态栏是不是影响 Activity 的生命周期，如果在 onStop() 的时候做了网络请求，onResume() 的时候怎么恢复**
- **Q：前台切换到后台，然后再回到前台，Activity 生命周期回调方法。弹出 Dialog，生命值周期回调方法。**
- **Q：Activity 生命周期**
- **Q：Activity 上有 Dialog 的时候按 Home 键时的生命周期**
- **Q：横竖屏切换的时候，Activity 各种情况下的生命周期**

Android 下拉通知栏不会影响 Activity 的生命周期方法。

弹出 Dialog，生命周期：其实是否弹出 Dialog，并不影响 Activity 的生命周期，所以这时和正常启动时 Activity 的生命回调方法一致: `onCreate() -> onStart() -> onResume()`。

![Activity 的生命周期](https://github.com/Shouheng88/Android-notes/raw/master/%E5%9B%9B%E5%A4%A7%E7%BB%84%E4%BB%B6/res/activity_life.png)

这里我们总结一下在实际的使用过程中可能会遇到的一些 Acitivity 的生命周期过程：

1. **当用户打开新的 Activity 或者切换回桌面**：会经过的生命周期为 `onPause()->onStop()`。因为此时 Activity 已经变成不可见了，当然，如果新打开的 Activity 用了透明主题，那么 onStop() 不会被调用，因此原来的 Activity 只是不能交互，但是仍然可见。
2. **从新的 Activity 回到之前的 Activity 或者从桌面回到之前的 Activity**：会经过的生命周期为 `onRestart()->onStart()-onResume()`。此时是从 onStop() 经 onRestart() 回到 onResume() 状态。
3. 如果在上述 1 的情况下，进入后台的 Activity 因为内存不足被销毁了，那么当再次回到该 Activity 的时候，生命周期方法将会从 onCreate() 开始执行到 onResume()。
4. **当用户按下 Back 键时**：如果当前 Activity 被销毁，那么经过的生命周期将会是 `onPause()->onStop()->onDestroy()`。

具体地，当存在两个 Activity，分别是 A 和 B 的时候，在各种情况下，它们的生命周期将会经过：

1. **Back 键 Home 键**
    1. 当用户点击 A 中按钮来到 B 时，假设 B 全部遮挡住了 A，将依次执行：`A.onPause()->B.onCreate()->B.onStart()->B.onResume->A.onStop()`。
    2. 接1，此时如果点击 Back 键，将依次执行：`B.onPause()->A.onRestart()->A.onStart()->A.onResume()->B.onStop()->B.onDestroy()`。
    3. 接2，此时如果按下 Back 键，系统返回到桌面，并依次执行：`A.onPause()->A.onStop()->A.onDestroy()`。
    4. 接2，此时如果按下 Home 键（非长按），系统返回到桌面，并依次执行`A.onPause()->A.onStop()`。由此可见，Back 键和 Home 键主要区别在于是否会执行 onDestroy()。
    5. 接2，此时如果长按 Home 键，不同手机可能弹出不同内容，Activity 生命周期未发生变化。
2. **横竖屏切换时 Activity 的生命周期**
    1. 不设置 Activity 的 `android:configChanges` 时，切屏会重新调用各个生命周期，切横屏时会执行一次，切竖屏时会执行两次。
    2. 设置 Activity 的 `android:configChanges=“orientation”` 时，切屏还是会重新调用各个生命周期，切横、竖屏时只会执行一次。
    3. 设置 Activity 的 `android:configChanges=“orientation|keyboardHidden”` 时，切屏不会重新调用各个生命周期，只会执行 onConfiguration() 方法。

- **Q：Activity 之间的通信方式**

1. Intent + `onActivityResult()` + `setResult()`
2. 静态变量（跨进程不行）
3. 全局通信，广播或者 EventBus

- **Q：AlertDialog, PopupWindow, Activity 区别**

AlertDialog 是 Dialog 的子类，所以它包含了 Dialog 类的很多属性和方法。是弹出对话框的主要方式，对话框分成支持包的和非支持包的，UI 效果上略有区别。

**AlertDialog 与 PopupWindow 之间最本质的差异在于**：

1. `AlertDialog 是非阻塞式对话框；而PopupWindow 是阻塞式对话框`。AlertDialog 弹出时，后台还可以做事情；PopupWindow 弹出时，程序会等待，在PopupWindow 退出前，程序一直等待，只有当我们调用了 `dismiss()` 方法的后，PopupWindow 退出，程序才会向下执行。我们在写程序的过程中可以根据自己的需要选择使用 Popupwindow 或者是 Dialog. 
2. `两者最根本的区别在于有没有新建一个 window`，PopupWindow 没有新建，而是通过 WMS 将 View 加到 DecorView；Dialog 是新建了一个 window (PhoneWindow)，相当于走了一遍 Activity 中创建 window 的流程。

Activity 与 Dialog 类似，都会使用 PhoneWindow 来作为 View 的容器。Activity 也可以通过设置主题为 Dialog 的来将其作为对话框来使用。Dialog 也可以通过设置 Theme 来表现得像一个 Activity 一样作为整个页面。但 Activity 具有生命周期，并且它的生命周期归 AMS 管，而 Dialog 不具有生命周期，它归 WMS 管。

- **Q：Activity 与 Service 通信的方式**

前提是是否跨进程，如果不跨进程的话，EventBus 和 静态变量都能传递信息，否则需要 IPC 才行：

1. Binder 用于跨进程的通信方式，AIDL 可以用来进行与远程通信，绑定服务的时候可以拿到远程的 Binder，然后调用它的方法就可以从远程拿数据。那么如果希望对远程的服务进行监听呢？可以使用 AIDL 中的 `oneway` 来定义回调的接口，然后在方法中传入回调即可。也可以使用 Messenger，向远程发送信息的时候，附带本地的 Messenger，然后远程获取本地的 Messenger 然后向其发送信息即可，详见 IPC 相关一文：[《Android 高级面试-2：IPC 相关》](https://juejin.im/post/5c6a9b6a6fb9a049f362a71f)
2. 广播：使用广播实现跨进程通信
3. 启动服务的时候传入值，使用 `startService()` 的方式

*关于 Activity 相关的内容可以参考笔者的文章：[《Android 基础回顾：Activity 基础》](https://blog.csdn.net/github_35186068/article/details/86380438)*

### 1.2 Service

- 怎么启动 Service
- Service 的开启方式
- Service 生命周期

![Service的生命周期图](https://github.com/Shouheng88/Android-notes/blob/master/%E5%9B%9B%E5%A4%A7%E7%BB%84%E4%BB%B6/res/service_life.png?raw=true)
其他，

1. Service 有绑定模式和非绑定模式，以及这两种模式的混合使用方式。不同的使用方法生命周期方法也不同。 
    1. **非绑定模式**：当第一次调用 `startService()` 的时候执行的方法依次为 `onCreate()->onStartCommand()`；当 Service 关闭的时候调用 `onDestory()`。
    2. **绑定模式**：第一次 `bindService()` 的时候，执行的方法为 `onCreate()->onBind()`；解除绑定的时候会执行 `onUnbind()->onDestory()`。
2. 我们在开发的过程中还必须注意 Service 实例只会有一个，也就是说如果当前要启动的 Service 已经存在了那么就不会再次创建该 Service 当然也不会调用 onCreate() 方法。所以，
    1. 当第一次执行 `startService(intent)` 的时候，会调用该 Service 中的 `onCreate()` 和`onStartCommand()` 方法。
    2. 当第二次执行 `startService(intent)` 的时候，只会调用该 Service 中的 `onStartCommand()` 方法。（因此已经创建了服务，所以不需要再次调用 `onCreate()` 方法了）。
3. `bindService()` 方法的第三个参数是一个标志位，这里传入 `BIND_AUTO_CREATE` 表示在Activity 和 Service 建立关联后自动创建 Service，这会使得 MyService 中的 `onCreate()` 方法得到执行，但 `onStartCommand()` 方法不会执行。所以，在上面的程序中当调用了`bindService()` 方法的时候，会执行的方法有，Service 的 `onCreate()` 方法，以及 ServiceConnection 的 `onServiceConnected()` 方法。
4. 在 3 中，如果想要停止 Service，需要调用 `unbindService()` 才行。 
5. 如果我们既调用了 `startService()`，又调用 `bindService()` 会怎么样呢？这时不管你是单独调用 `stopService()` 还是 `unbindService()`，Service 都不会被销毁，必须要将两个方法都调用 Service 才会被销毁。也就是说，`stopService()` 只会让 Service 停止，`unbindService()` 只会让 Service 和 Activity 解除关联，一个 Service 必须要在既没有和任何 Activity 关联又处理停止状态的时候才会被销毁。

- 进程保活
- App 中唤醒其他进程的实现方式

### 1.3 Broadcast

- BroadcastReceiver，LocalBroadcastReceiver 区别
- 广播的使用场景
- 广播的使用方式，场景
- 广播的分类？
- 广播（动态注册和静态注册区别，有序广播和标准广播）

分类

1. 按照注册方式：**静态注册和动态注册**两种：
    1. 静态广播直接在 manifest 中注册。限制：
        1. 在 Android 8.0 的平台上，应用不能对大部分的广播进行静态注册，也就是说，不能在 AndroidManifest 文件对**有些**广播进行静态注册；
        2. 当程序运行在后台的时候，静态广播中不能启动服务。
    2. 动态广播与静态广播相似，但是不需要在 Manifest 中进行注册。**注意当页面被销毁的时候需要取消注册广播！**
2. 按照作用范围：**本地广播和普通广播**两种，
    1. 普通广播是全局的，所有应用程序都可以接收到，容易会引起安全问题。
    2. 本地广播只能够在应用内传递，广播接收器也只能接收应用内发出的广播。本地广播的核心类是 LocalBroadcastManager，使用它的静态方法 `getInstance()` 获取一个单例之后就可以使用该单例的 `registerReceiver()`、`unregisterReceiver()` 和 `sendBroadcast()` 等方法来进行操作了。
3. 按照是否有序：**有序广播和无序广播**两种，无序广播各接收器接收的顺序无法确定，并且在广播发出之后接收器只能接收，不能拦截和进行其他处理，两者的区别主要体现在发送时调用的方法上。优先级高的会先接收到，优先级相等的话则顺序不确定。并且前面的广播可以在方法中向 Intent 写入数据，后面的广播可以接收到写入的值。

### 1.4 ContentProvider

- **Q：Android 系统为什么会设计 ContentProvider，进程共享和线程安全问题**

ContentProvider 在 Android 中的作用是对外共享数据，提供了数据访问接口，用于在不同应用程序之间共享数据，同时还能保证被访问数据的安全性。它通常用来提供一些公共的数据，比如用来查询文件信息，制作音乐播放器的时候用来读取系统中的音乐文件的信息。

与 SQLiteDatabase 不同，ContentProvider 中的 CRUD 不接收表名参数，而是 Uri 参数。内容 URI 是内容提供器中数据的唯一标识符，包括权限和路径。

并发访问时，不论是不同的进程还是同一进程的不同线程，当使用 AMS 获取 Provider 的时候返回的都是同一实例。我们使用 Provider 来从远程访问数据，当 `query()` 方法运行在不同的线程，实际上是运行在 Provider 方的进程的 Binder 线程池中。通过 Binder 的线程池来实现多进程和多线程访问的安全性。

*参考：[Android ContentProvider的线程安全（一）](https://blog.csdn.net/zhanglianyu00/article/details/78362960)*

### 1.5 Fragment

- **Q：Fragment 各种情况下的生命周期**
- **Q：Activity 与 Fragment 之间生命周期比较**

![](https://github.com/Shouheng88/Android-notes/raw/master/%E5%9B%9B%E5%A4%A7%E7%BB%84%E4%BB%B6/res/activity_fragment_lifecycle.png)

- Fragment 之间传递数据的方式？

1. 同一 Activity 的 Fragment 之间可以使用 ViewModel 来交换数据；
2. 使用 EventBus，广播，静态的；
3. 通过 Activity 获取到另一个 Fragment，强转之后使用它对外提供的 public 方法进行通信；
4. 通过 Activity 获取到另一个 Fragment，该 Fragment 实现某个接口，然后转成接口之后进行通信（也适用于 Activity 与 Fragment 之间），强转之后使用它对外提供的 public 方法进行通信；

- 如何实现 Fragment 的滑动

### 1.6 Context

- **Q：Application 和 Activity 的 Context 对象的区别**

### 1.7 其他

- AndroidManifest 的作用与理解

## 2、Android API

### 2.1 AsyncTask

1. **AsyncTask 机制，如何取消 AsyncTask**
2. **多线程（关于 AsyncTask 缺陷引发的思考）**
3. **Asynctask 有什么优缺点**
- AsyncTask 机制、原理及不足？

AsyncTask 是 Android 提供的用来执行异步操作的 API，我们可以通过它来执行异步操作，并在得到结果之后将结果放在主线程当中进行后续处理。

AsyncTask 的缺点是在使用多个异步操作和并需要进行 Ui 变更时，就变得复杂起来（会导致多个 AsyncTask 进行嵌套）。如果有多个地方需要用到 AsyncTask，可能需要定义多个 AsyncTask 的实现。

如果 AsyncTask 以一个非静态的内部类的形式声明在 Activity 中，那么它会持有 Activity 的匿名引用，如果销毁 Activity 时 AsyncTask 还在执行异步任务的话，Activity 就不能销毁，会造成内存泄漏。解决方式是，要么将 AsyncTask 定义成静态内部类，要么在 Activity 销毁的时候调用 `cancel()` 方法取消 AsyncTask.在屏幕旋转或 Activity 意外结束时，Activity 被创建，而 AsyncTask 会拥有之前 Activity 的引用，会导致结果丢失。

AsyncTask 在 1.6 之前是串行的，1.6 之后是并行的，3.0 之后又改成了串行的。不过我们可以通过调用 `executeOnExecutor()` 方法并传入一个线程池，来让 AsyncTask 在某个线程池中并行执行任务。

AsyncTask 的源码就是将一个任务封装成 Runnable 之后放进线程池当中执行，执行完毕之后调用主线程的 Handler 发送消息到主线程当中进行处理。任务在默认线程池当中执行的时候，会被加入到一个双端队列中执行，执行完一个之后再执行下一个，以此来实现任务的串行执行。

*了解 AsyncTask 的源码，可以参考笔者的这篇文章：[Android AsyncTask 源码分析](https://juejin.im/post/5b65c71af265da0f9402ca4a)*

- **Q：介绍下 SurfaceView**

SurfaceView 以及 TextureView 均继承于 `android.view.View`，它们都在`独立的线程`中绘制和渲染，常被用在对绘制的速率要求比较高的应用场景中，用来解决普通 View 因为绘制的时间延迟而带来的`掉帧`的问题，比如用作相机预览、视频播放的媒介等。

SurfaceView 提供了嵌入在视图层次结构内的专用绘图图层 (Surface)。图层 (Surface) 处于 `Z 轴`，位于持有 SurfaceView 的窗口之后。SurfaceView 在窗口上开了一个透明的 “洞” 以展示图面。Surface 的排版显示受到视图层级关系的影响，它的兄弟视图结点会在顶端显示。注意，如果 Surface 上面有透明控件，那么每次 Surface 变化都会引起框架重新计算它和顶层控件的透明效果，这会影响性能。SurfaceView 使用双缓冲，SurfaceView 自带一个 Surface，这个 Surface 在 WMS 中有自己对应的WindowState，在 SurfaceFlinger 中也会有自己的 Layer。这样的好处是对这个Surface的渲染可以放到单独线程去做。因为这个 Surface 不在 View hierachy 中，它的显示也不受 View 的属性控制，所以不能进行平移，缩放等变换，也不能放在其它 ViewGroup 中，一些 View 中的特性也无法使用。

TextureView 在 Andriod 4.0 之后的 API 中才能使用，并且必须在硬件加速的窗口中。和 SurfaceView 不同，它不会在 WMS 中单独创建窗口，而是作为 View hierachy 中的一个普通 View，因此可以和其它普通 View 一样进行移动，旋转，缩放，动画等变化。它占用内存比 SurfaceView 高，在 5.0 以前在主线程渲染，5.0 以后有单独的渲染线程。

*更多内容请参考：[Android：解析 SurfaceView & TextureView](https://blog.csdn.net/github_35186068/article/details/87895365)*

### 2.2 View 体系

事件分发机制等

- Android 事件分发机制
- 事件传递机制的介绍
- View 事件传递
- 触摸事件的分发？

Activity 的层级：`Activity->PhoneWindow->DecorView`

当触摸事件发生的时候，首先会被 Activity 接收到，然后该 Activity 会通过其内部的 `dispatchTouchEvent(MotionEvent)` 将事件传递给内部的 `PhoneWindow`；接着 `PhoneWindow` 会把事件交给 `DecorView`，再由 `DecorView` 交给根 `ViewGroup`。剩下的事件传递就只在 `ViewGroup` 和 `View` 之间进行。

事件分发机制本质上是一个`深度优先`的遍历算法。事件分发机制的核心代码：

```java
    boolean dispatchTouchEvent(MotionEvent e) {
        boolean result;
        if (onInterceptTouchEvent(e)) { // 父控件可以覆写并返回 true 以拦截
            result = super.dispatchTouchEvent(e); // 调用 View 中该方法的实现
        } else {
            for (child in children) {
                result = child.dispatchTouchEvent(e); // 这里的 child 分成 View 和 ViewGroup 两者情形
                if (result) break; // 被子控件消费，停止分发
            }
        }
        return result;
    }
```

对于 `dispatchTouchEvent()` 方法，在 View 的默认实现中，会先交给 `onTouchEvent()` 进行处理，若它返回了 true 就消费了，否则根据触摸的类型，决定是交给 `OnClickListener` 还是 `OnLongClickListener` 继续处理。

*事件分发机制和 View 的体系请参考笔者文章：[View 体系详解：坐标系、滑动、手势和事件分发机制](https://juejin.im/post/5bbb5fdce51d450e942f6be4)，整体上事件分发机制应该分成三个阶段来进行说明：1).从 Activity 到 DecorView 的过程；2).ViewGroup 中的分发的过程；3).交给 View 之后的实现过程。*

- 封装 View 的时候怎么知道 View 的大小
- 点击事件被拦截，但是想传到下面的 View，如何操作？
- 计算一个 view 的嵌套层级

按照广度优先算法进行遍历

### 2.3 列表控件

- ListView 的优化
- ListView 重用的是什么

ListView 默认缓存一页的 View，也就是你当前 Listview 界面上有几个 Item 可以显示,，Lstview 就缓存几个。当现实第一页的时候，由于没有一个 Item 被创建，所以第一页的 Item 的 `getView()` 方法中的第二个参数都是为 null 的。

ViewHolder 同样也是为了提高性能。就是用来在缓存使用 `findViewById()` 方法得到的控件，下次的时候可以直接使用它而不用再进行 `find` 了。

*关于 ListView 的 ViewHolder 等的使用，可以参考这篇文章：[ListView 复用和优化详解](https://blog.csdn.net/u011692041/article/details/53099584)*

- RecycleView 的使用，原理，RecycleView 优化
- recycleview Listview 的区别，性能



- [ ] Listview 图片加载错乱的原理和解决方案

### 2.4 其他控件

- LinearLayout、RelativeLayout、FrameLayout 的特性、使用场景
- ViewPager 使用细节，如何设置成每次只初始化当前的 Fragment，其他的不初始化

### 2.5 数据存储

- Android 中数据存储方式

SP，SQLite，ContentProvider，File，Server

## 3、架构相关

- 模块化实现（好处，原因）
- 项目组件化的理解
- 模式 MVP、MVC 介绍
- MVP 模式

## 4、系统源码

- [ ] App 启动流程，从点击桌面开始
- [ ] activity 栈


- [ ] 画出 Android 的大体架构图


- [ ] 权限管理系统（底层的权限是如何进行 grant 的）
- [ ] 动态权限适配方案，权限组的概念

- [ ] App 是如何沙箱化，为什么要这么做

- [ ] 描述清点击 Android Studio 的 build 按钮后发生了什么

编译打包的过程->adb->安装过程 PMS->应用启动过程 AMS

- [ ] 大体说清一个应用程序安装到手机上时发生了什么

- [ ] 系统启动流程 Zygote进程 –> SystemServer进程 –> 各种系统服务 –> 应用进程

- [ ] ActicityThread 相关？

- [ ] 应用安装过程

- [ ] 简述 Activity 启动全部过程？

