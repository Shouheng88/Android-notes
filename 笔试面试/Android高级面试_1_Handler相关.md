# Android 高级面试-1：Handler 相关

**问题：Handler 实现机制（很多细节需要关注：如线程如何建立和退出消息循环等等）**    
**问题：关于 Handler，在任何地方 new Handler 都是什么线程下?**     
**问题：Handler 发消息给子线程，looper 怎么启动?**   
**问题：在子线程中创建 Handler 报错是为什么?**   
**问题：如何在子线程创建 Looper?**    
**问题：为什么通过 Handler 能实现线程的切换?**

参考：

1. `Handler 机制`：Handler 机制中有 4 个主要的对象：Handler、Message、MessageQueue 和 Looper. 
    1. Handler 负责消息的发送和处理；
    2. Message 是消息对象，是`链表`（不是队列！）的一个结点。Message 的布局变量保存了 Handler，处理消息时就是从 Message 上获取 Handler，并调用它的 `dispatchMessage()`，并进一步回调 `handleMessage()` 方法执行我们的逻辑。
    3. MessageQueue 是消息队列，用于存放消息对象的数据结构；
    4. Looper 是消息队列的处理者，用于轮询消息队列，使用 MessageQueue 取出 Message。

2. `线程的问题`：

    1. 当我们在某个线程当中调用 `new Handler()` 的时候会使用当前线程的 Looper 创建 Handler. 当前线程的 Looper 存在于线程局部变量 ThreadLocal 中。
    2. 在使用 Handler 之前我们需要先调用 `Looper.prepare()` 方法实例化当前线程的 Looper，并将其放置到当前线程的线程局部变量中。
    3. 一个线程的 Looper 只会被创建一次，之后会先从 ThreadLocal 中获取再使用。
    4. 调用 `Looper.prepare()` 时会调用 Looper 的构造方法，并在构造方法中初始化 MessageQueue. 
    5. 当我们调用 `Looper.loop()` 时开启消息循环。
    6. 主线程的 Looper 在 ActivityThread 的静态 `main()` 方法中被创建。主线程的 Looper 跟其他线程有所区别，主线程的 Looper 不能停止。我们可以使用 `Looper.getMainLooper()` 方法来获取主线程的 Looper，并使用它来创建 Handler. 
    
    所以，在非主线程中使用 Handler 的标准是：

```java
Looper.prepare(); // 内部会调用 Looper 的 new 方法实例化 Looper 并将其放进 TL
new Handler().post(() -> /* do something */);
Looper.loop();
```

4. `在 Looper 的 loop() 中开启无限循环为什么不会导致主线程 ANR 呢？` 这是因为 Android 系统本身就是基于消息机制的，而 Looper 的 循环就是来处理这些消息的。造成卡顿和 ANR 是因为某个消息阻塞了 Looper 循环，导致界面消息得不到处理，而不是 Looper 循环本身。并且如果 Looper 中没有消息需要处理，循环将会结束，线程也就关闭了。

5. `Handler 内存泄漏及解决办法`：如果 Handler 不是静态内部类，Handler 会持有 Activity 的匿名引用。当 Activity 要被回收时，因为 Handler 在做耗时操作没有被释放，Handler Activity 的引用不能被释放导致 Activity 没有被回收停留在内存中造成内存泄露。 解决方法是：

    1. 将 Handler 设为静态内部类，如果需要的话，使用弱引用引用外部的 Activity；
    2. 在 Activity 生命周期 `onDestroy()` 中调用 `Handler.removeCallbacks()` 方法。

**问题：Handler.post() 的逻辑在哪个线程执行的，是由 Looper 所在线程还是 Handler 所在线程决定的？**    
**问题：Handler 的 post()/send() 的原理？**    
**问题：Handler 的 post() 和 postDelayed() 方法的异同？**

`post()` 方法所在的线程由 Looper 所在线程决定的；最终逻辑是在 `Looper.loop()` 方法中，从 MQ 中拿出 Message，并且执行其逻辑。这是在 Looper 中执行的。因此由 Looper 所在线程决定。

不论你调用 `send()` 类型的方法还是 `post()` 类型的方法，最终都会调用到 `sendMessageAtTime()` 方法。`post()` 和 `postDelay()` 的区别在于，前者使用当前时间，后者使用当前时间+delay 的时间来决定消息触发的时间。最终方法的参数都将被包装成一个 Message 对象加入到 Handler 对应的 Looper 的 MQ 中被执行。

**问题：Looper 和 Handler 一定要处于一个线程吗？子线程中可以用 MainLooper 去创建 Handler吗？**

Looper 和 Handler 不需要再一个线程中，默认的情况下会从 TL 中取当前线程对应的 Looper，但我们可以通过显式地指定一个 Looper 的方式来创建 Handler. 比如，当我们想要在子线程中发送消息到主线程中，那么我们可以

```java
Handler handler = new Handler(Looper.getMainLooper());
```

**问题：Handler.post() 方法发送的是同步消息吗？可以发送异步消息吗？**

用户层面发送的都是同步消息，不能发送异步消息；异步消息只能由系统发送。

**问题：MessageQueue.next() 会因为发现了延迟消息，而进行阻塞。那么为什么后面加入的非延迟消息没有被阻塞呢？**    
**问题：MessageQueue.enqueueMessage() 方法的原理，如何进行线程同步的？**    
**问题：MessageQueue.next() 方法内部的原理？**    
**问题：next() 是如何处理一般消息的？**    
**问题：next() 是如何处理同步屏障的？**     
**问题：next() 是如何处理延迟消息的？**

1. 创建 Looper 时会同时创建一个 MQ，而 MQ 同时又会调用 `nativeInit()` 方法在 Native 层实例化一个 MQ 和 Looper，并返回 Native 的 MQ 对象的指针. 
2. Java 层的 Looper 和 Native 层的 Looper 之间使用 epoll 进行通信。
3. 当调用 Looper 的 `loop()` 方法的时候会启动一个 for 循环来对消息进行处理。它调用 MQ 的 `next()` 方法尝试获取消息，这个方法也是一个 for 循环，它调用 `nativePollOnce()` 向管道写入一个消息，并等待返回，如果没有消息这里就会阻塞。当拿到了返回结果之后，这里继续向下进行处理，从 Message 中读取消息并进行处理。
4. 在线程安全方面，当从 `nativePollOnce()` 中返回之后，使用 `sychronized(this)` 对 MQ 进行加锁来保证线程安全。
5. 当使用 Handler 向 MQ 中添加消息时，会根据消息触发时间决定它在链表中的位置，时间早的位于链表的头结点。然后，如果此时 MQ 处于阻塞状态，那么就会调用 `nativeWake()` 方法向管道中写入消息，这样 MQ 就从 `nativePollOnce()` 中返回了。
6. `同步屏障`用来立即推迟所有将要执行的同步消息，知道释放同步屏障。使用 `postSyncBarrier()` 进行同步屏障，使用 `removeSyncBarrier()` 结束同步屏障。前者会返回一个 token，然后我们将其传入到 `removeSyncBarrier()` 中结束当前的同步屏障。进行内存屏障的时候会创建一个立即执行的消息，并将其添加到 MQ 中。当尝试获取消息的时候就可能会在 `nativePollOnce()` 阻塞。释放同步屏障的时候会从链表中找到这个结点，并可能调用 `nativeWake()` 方法。对于同步类型的消息，即使发生了同步屏障，它也会被正常执行。
7. 同步屏障的使用案例：ViewRootImpl 中，`scheduleTraversals()` 方法在遍历 View 树之前会进行同步屏障。（猜测是用来暂停非 UI 绘制的消息，UI 绘制完毕之后再恢复执行。）

**问题：Handler 的 dispatchMessage() 分发消息的处理流程？**    
**问题：Handler 为什么要有 Callback 的构造方法？**

使用 Handler 的时候我们会覆写 Handler 的 `handleMessage()` 方法。当我们调用该 Handler 的 `send()` 或者 `post()` 发送一个消息的时候，发送的信息会被包装成 Message，并且将该 Message 的 target 指向当前 Handler，这个消息会被放进 Looper 的 MQ 中。然后在 Looper 的循环中，取出这个 Message，并调用它的 target Handler，也就是我们定义的 Handler 的 `dispatchMessage()` 方法处理消息，此时会调用到 Handler 的  `handleMessage()` 方法处理消息，并回调 Callback. 

当 Handler 在消息队列中被执行的时候会直接调用 Handler 的 `dispatchMessage()` 方法回调 Callback.

**问题：Looper 的两个退出方法？**     
**问题：quit() 和 quitSafely() 有什么区别**    
**问题：子线程中创建了 Looper，在使用完毕后，终止消息循环的方法？**    
**问题：quit() 和 quitSafely() 的本质是什么？**    

```java
    public void quit() {
        mQueue.quit(false);
    }
    public void quitSafely() {
        mQueue.quit(true);
    }
    void quit(boolean safe) {
        if (!mQuitAllowed)  throw new IllegalStateException("Main thread not allowed to quit.");
        synchronized (this) {
            if (mQuitting) return;
            mQuitting = true;
            if (safe)  removeAllFutureMessagesLocked(); // 把所有延迟消息清除
            else       removeAllMessagesLocked();  // 直接把消息队列里面的消息清空
            nativeWake(mPtr); // 唤醒
        }
    }
    public static void loop() {
        // ...
        for (;;) {
            Message msg = queue.next();
            if (msg == null) { // 得到了 null 就返回了
                return;
            }
            // ...
        }
    }
    // MQ:
    Message next() {
        // ...
        for (;;) {
            // ...
            nativePollOnce(ptr, nextPollTimeoutMillis);
            synchronized (this) {
                // ... 这里就返回了 null
                if (mQuitting) {
                    dispose();
                    return null;
                }
                // ...
            }
        }
     }
```

`quit()` 和 `quitSafely()` 的本质就是让消息队列的 `next()` 返回 null，以此来退出 `Looper.loop()`。

`quit()` 调用后直接终止 Looper，不在处理任何 Message，所有尝试把 Message 放进消息队列的操作都会失败，比如 `Handler.sendMessage()` 会返回 false，但是存在不安全性，因为有可能有 Message 还在消息队列中没来的及处理就终止 Looper 了。

`quitSafely()` 调用后会在所有消息都处理后再终止 Looper，所有尝试把 Message 放进消息队列的操作也都会失败。

**问题：Looper.loop() 在什么情况下会退出？**

1. `next()` 方法返回的 msg == null；
2. 线程意外终止。

**问题：Looper.loop() 的源码流程?**

1. 获取到 Looper 和消息队列；
2. for 无限循环，阻塞于消息队列的 `next()` 方法；
3. 取出消息后调用 `msg.target.dispatchMessage(msg)` 进行消息分发。

**问题：Looper.loop() 方法执行时，如果内部的 myLooper() 获取不到Looper会出现什么结果?**

```java
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        // ...
    }
```

**问题：Android 如何保证一个线程最多只能有一个 Looper？如何保证只有一个 MessageQueue**

通过保证只有一个 Looper 来保证只有以一个 MQ. 在一个线程中使用 Handler 之前需要使用 `Looper.prepare()` 创建 Looper，它会从 TL 中获取，如果发现 TL 中已经存在 Looper，就抛异常。

**问题：Handler 消息机制中，一个 Looper 是如何区分多个 Handler 的？**

根据消息的分发机制，Looper 不会区分 Handler，每个 Handler 会被添加到 Message 的 target 字段上面，Looper 通过调用 `Message.target.handleMessage()` 来让 Handler 处理消息。

## 参考资料

1. [《Android 中的 Handler 的 Native 层研究》](https://www.liangzl.com/get-article-detail-14435.html)
