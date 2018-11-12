# 关于 Activity 

## 1、生命周期

下图是一般情况下一个Activity将会经过的生命周期的流程图：

[Activity的生命周期](https://github.com/Shouheng88/Awesome-Android/blob/master/%E5%9B%9B%E5%A4%A7%E7%BB%84%E4%BB%B6/res/activity_life.png?raw=true)

关于上图中生命周期方法的说明：

1. **onCreate/onDestroy**：onCreate表示Activity正在被创建，可以用来做初始化工作；onDestroy表示Activity正在被销毁，可以用来做释放资源的工作；
2. **onStart/onStop**：onStart在Activity从不可见变成可见的时候被调用；onStop在Activity从可见变成不可见的时候被调用；
3. **onRestart**：在Activity从不可见到变成可见的过程中被调用；
4. **onResume/onPause**：onResume在Activity可以与用户交互的时候被调用，onPause在Activity不可与用户交互的时候被调用。

所以根据上面的分析，我们可以将Activity的生命周期概况为：`创建->可见->可交互->不可交互->不可见->销毁`。因此，我们可以得到下面的这张图：

[从另一个角度来看生命周期](https://github.com/Shouheng88/Awesome-Android/blob/master/%E5%9B%9B%E5%A4%A7%E7%BB%84%E4%BB%B6/res/activity_life2.png?raw=true)

这里我们总结一下在实际的使用过程中可能会遇到的一些Acitivity的生命周期过程：

1. 当用户打开新的Activity或者切换回桌面时，会经过的生命周期：onPause->onStop。因为此时Activity已经变成不可见了，当然，如果新打开的Activity用了透明主题，那么onStop不会被调用，因此原来的Activity只是不能交互，但是仍然可见。
2. 从新的Activity回到之前的Activity或者从桌面回到之前的Activity，会经过的生命周期：`onRestart->onStart-onResume`。此时是从onStop经onRestart回到onResume状态。
3. 如果在1的状态的时候，原来的Activity因为内存不足被销毁了，那么生命周期方法将会从onCreate开始执行到onResume。
4. 当用户按下Back键时如果当前Activity被销毁，将会经过生命周期：`onPause->onStop->onDestroy`。






## 3、一些操作中生命周期的总结

### 3.1 Activity切换 Back键 Home键

1. 当用户点击A中按钮来到B时，假设B全部遮挡住了A，将依次执行：`A.onPause()->B.onCreate()->B.onStart()->B.onResume->A.onStop()`。
2. 此时如果点击Back键，将依次执行：`B.onPause()->A.onRestart()->A.onStart()->A.onResume()->B.onStop()->B.onDestroy()`。
3. 接2，此时如果按下Back键，系统返回到桌面，并依次执行`A.onPause()->A.onStop()->A.onDestroy()`。
4. 接2，此时如果按下Home键（非长按），系统返回到桌面，并依次执行`A.onPause()->A.onStop()`。由此可见，Back键和Home键主要区别在于是否会执行onDestroy。
5. 接2，此时如果长按Home键，不同手机可能弹出不同内容，Activity生命周期未发生变化。

### 3.2 横竖屏切换时候Activity的生命周期

1. 不设置Activity的`android:configChanges`时，切屏会重新调用各个生命周期，切横屏时会执行一次，切竖屏时会执行两次。
2. 设置Activity的`android:configChanges=“orientation”`时，切屏还是会重新调用各个生命周期，切横、竖屏时只会执行一次。
3. 设置Activity的`android:configChanges=“orientation|keyboardHidden”`时，切屏不会重新调用各个生命周期，只会执行onConfiguration方法。

参考:

1. [深入理解Activity的生命周期](http://www.jianshu.com/p/fb44584daee3)
2. [Android总结篇系列：Activity生命周期](https://www.cnblogs.com/lwbqqyumidi/p/3769113.html)







走马观花，把 Activity 看个明白

这篇文章中我们将来彻底梳理一下 Android 当中的 Activity. 

## Activity 

在讲 Activity 的工作流程之前应该先大体上讲一下其工作的流程，然后介绍一下与其相关的几个主要类的作用。

![Activity启动过程的示意图](res/ActivityThread示意图.png)

因为其涉及的类比较多，它们又分别在不同的时机扮演不同的角色，如果没有在开始的时候对其进行一一介绍的话，理解起来比较费力。

- ActivityManagerService ：https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ActivityManagerService.java



我们首先分析下 Android 中非常常用的两种通信机制，一个是跨进程的通信机制 Binder，另一个是进程中的通信机制 Handler. Handler 会为每个线程创建一个消息队列，你的任务会被加入到消息队列中执行。同时它可以用来实现跨线程的调用。笔者在之前的文章中分析过 Handler 的用法，可以参考这篇文章 [《ssss》]()。笔者曾分析过 Binder 相关的东西，这里我们先简单介绍下：

通常我们不会直接创建 Binder 相关的类，而是使用 AIDL。在 Android 的 framework 层代码里包含了太多的 AIDL 调用。AIDL 是一种接口描述语言，用来简化 Binder 使用的。在 AIDL 中有几个重要的角色：

- Stub
- Proxy

实际上在 Android 的 framework 层中有许多跨进程的调用，只是它们往往被封装成了 Manager 的形式，所以你感受不到它的存在而已。


抛开系统启动的过程不讲，这里我们分析下系统启动完毕，启动了某个应用之后的逻辑。当启动某个应用的时候，我们的 ActivityThread 的 main() 静态方法将会被触发。在这个方法中主要的工作有：

1. 准备主线程的 Looper；
2. 初始化一个 ActivityThread 实例，会同时创建 H；
3. 创建一个 Application 实例；？？？
4. 主线程的 Looper 开始循环监听。

![Activity启动过程的示意图](res/ActivityThread示意图.png)

startActivity 拥有多个重载方法，但是它们最后都将调用下面的这个方法：

    public void startActivityForResult(@RequiresPermission Intent intent, int requestCode, @Nullable Bundle options) {
        if (mParent == null) {
            options = transferSpringboardActivityOptions(options);
            Instrumentation.ActivityResult ar =
                mInstrumentation.execStartActivity(
                    this, mMainThread.getApplicationThread(), mToken, this, intent, requestCode, options);
            if (ar != null) {
                mMainThread.sendActivityResult(
                    mToken, mEmbeddedID, requestCode, ar.getResultCode(), ar.getResultData());
            }
            if (requestCode >= 0) {
                mStartedActivity = true;
            }
            cancelInputsAndStartExitTransition(options);
        } else {
            if (options != null) {
                mParent.startActivityFromChild(this, intent, requestCode, options);
            } else {
                mParent.startActivityFromChild(this, intent, requestCode);
            }
        }
    }

然后，会调用 Instrumentation 的 execStartActivity() 方法，这里传入了主线程的 ApplicationThread. ApplicationThread 在实例化 ActivityThread 的时候会被初始化。它继承自 IApplicationThread.Stub，因此是一个运行在服务端的实例。显然，它在这里是将我们的应用进程当作服务端来给 AMS 进行调用了。（随后你将看到 AMS 中是如何调用 ApplicationThread 的方法来向主线程发送消息，从而回调 Activity 的生命周期方法的。）


在 9.0 的代码中回调 Activity 的生命周期的过程与以往的代码有所不同。你将看到书本上面讲到的那些 H 中定义的常量不存在了。改变的主要是 Activity 的生命周期回调的代码，因为 Android 在后来加入了 



