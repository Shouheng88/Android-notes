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
