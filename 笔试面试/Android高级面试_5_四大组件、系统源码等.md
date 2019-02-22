# Android 高级面试-0：四大组件、系统源码等

## 1、四大组件

### 1.1 Activity

- 在两个 Activity 之间传递对象还需要注意什么呢？

对象的大小，对象的大小，对象的大小！！！重要的事情说三遍，一定要注意对象的大小。Intent 中的 Bundle 是使用 Binder 机制进行数据传送的。能使用的 Binder 的缓冲区是有大小限制的（有些手机是 2 M），而一个进程默认有 16 个 Binder 线程，所以一个线程能占用的缓冲区就更小了（有人以前做过测试，大约一个线程可以占用 128 KB）。所以当你看到 The Binder transaction failed because it was too large 这类 TransactionTooLargeException 异常时，你应该知道怎么解决了。

- [ ] singleTask 启动模式

- [ ] 下拉状态栏是不是影响 activity 的生命周期，如果在 onStop 的时候做了网络请求，onResume 的时候怎么恢复

- [ ] Activity 启动模式


- [ ] Activity 与 Service 通信的方式

- [ ] Activity 之间的通信方式

- [ ] 前台切换到后台，然后再回到前台，Activity 生命周期回调方法。弹出 Dialog，生命值周期回调方法。

- [ ] Android 进程分类

- [ ] Activity 与 Fragment 之间生命周期比较

- [ ] Activity 生命周期
- [ ] AlertDialog, popupWindow, Activity 区别
- [ ] fragment 各种情况下的生命周期
- [ ] Activity 上有 Dialog 的时候按 home 键时的生命周期
- [ ] 横竖屏切换的时候，Activity 各种情况下的生命周期
- [ ] Application 和 Activity 的 context 对象的区别


### 1.2 Service

- [ ] 怎么启动 service，service 和 activity 怎么进行数据交互

- [ ] 进程保活
- [ ] App 中唤醒其他进程的实现方式

- [ ] Service 的开启方式

- [ ] service 生命周期

### 1.3 Broadcast

- [ ] BroadcastReceiver，LocalBroadcastReceiver 区别
- [ ] 广播的使用场景

- [ ] 广播的使用方式，场景
- [ ] 广播的分类？
- [ ] 广播（动态注册和静态注册区别，有序广播和标准广播）


### 1.4 ContentProvider

- [ ] Android 系统为什么会设计 ContentProvider，进程共享和线程安全问题


### 1.5 Fragment

- [ ] 如何实现 Fragment 的滑动

- [ ] fragment 之间传递数据的方式？

### 1.6 其他

- [ ] AndroidManifest 的作用与理解

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

- [ ] 介绍下 SurfView

### 2.2 View 体系

事件分发机制等

- [ ] Android 事件分发机制
- [ ] 事件传递机制的介绍
- [ ] View 事件传递
- [ ] 封装 view 的时候怎么知道 view 的大小
- [ ] view 渲染

- [ ] 触摸事件的分发？
- [ ] 点击事件被拦截，但是相传到下面的 view，如何操作？

- [ ] 计算一个 view 的嵌套层级

### 2.3 列表控件

- [ ] ListView 的优化
- [ ] ListView 重用的是什么

- [ ] RecycleView 的使用，原理，RecycleView 优化
- [ ] recycleview listview 的区别,性能

- [ ] listview 图片加载错乱的原理和解决方案

### 2.4 其他控件

- LinearLayout、RelativeLayout、FrameLayout 的特性、使用场景
- ViewPager 使用细节，如何设置成每次只初始化当前的 Fragment，其他的不初始化

### 2.5 数据存储

- [ ] Android 中数据存储方式

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

