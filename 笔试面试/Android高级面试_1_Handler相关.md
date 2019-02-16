# Android 高级面试-1：Handler 相关

## 要点

难点：

1. MQ 的 next() 方法，enqueueMessage() 方法，因为它们与 Native 层的 Looper 和 MQ 关联。

重点：

1. 消息如何分发
2. next() 方法
3. 如何退出
4. Handler 与线程对应起来的原理

## 题目

- **Handler 实现机制（很多细节需要关注：如线程如何建立和退出消息循环等等）**
- **关于 Handler，在任何地方 new Handler 都是什么线程下?**
- **Handler 发消息给子线程，looper 怎么启动?**
- **在子线程中创建 Handler 报错是为什么?**
- **如何在子线程创建 Looper?**
- **为什么通过 Handler 能实现线程的切换?**

Handler 机制中有 4 个主要的对象：Handler、Message、MessageQueue 和 Looper. Handler 负责消息的发送和处理；Message 是消息对象，类似于链表的一个结点；MessageQueue 是消息队列，用于存放消息对象的数据结构；Looper 是消息队列的处理者（用于轮询消息队列的消息对象，取出后回调 handler 的 `dispatchMessage()` 进行消息的分发，`dispatchMessage()` 方法会回调 `handleMessage()` 方法把消息传入，由 Handler 的实现类来处理。）

当我们在某个线程当中调用 `new Handler()` 的时候会使用当前线程的 Looper 创建 Handler. 当前线程的 Looper 存在于线程局部变量 ThreadLocal 中。在使用 Handler 之前我们需要先调用 `Looper.prepare()` 方法实例化当前线程的 Looper，并将其放置到当前线程的线程局部变量中（只放一次，以后会先从 TL 中获取再使用，**此时会调用 Looper 的构造方法，并在构造方法中初始化 MQ**），然后**调用 `Looper.loop()` 开启消息循环**。主线程也是一样，只是主线程的 Looper 在 ActivityThread 的 `main()` 方法中被实例化。我们可以使用 `Looper.getMainLooper()` 方法来获取主线程的 Looper，并使用它来创建 Handler，这样我们就可以在任何线程中向主线程发送消息了。

```java
    Looper.prepare(); // 内部会调用 Looper 的 new 方法实例化 Looper 并将其放进 TL
    new Handler().post(() -> /* do something */);
    Looper.loop();
```

当实例化 Looper 的时候会同时实例化一个 MessageQueue，而 MessageQueue 同时又会调用 Native 层的方法在 Native 层实例化一个 MessageQueue 还有 Looper. Java 层的 Looper 和 Native 层的 Looper 之间使用 epoll 进行通信。当调用 Looper 的 `loop()` 方法的时候会启动一个循环来对消息进行处理。Java 层的 MQ 中没有消息的时候，Native 层的 Looper 会使其进入睡眠状态，当有消息到来的时候再将其唤醒起来处理消息，以节省 CPU. 

在 Looper 的 `loop()` 中开启无限循环为什么不会导致主线程 ANR 呢？这是因为 Android 系统本身就是基于消息机制的，所谓的消息就是指发送到主线程当中的消息。之所以产生 ANR 并不是因为主线程当中的任务无限循环，而是因为无限循环导致其他的事件得不到处理。

[《Android 消息机制：Handler、MessageQueue 和 Looper》](https://juejin.im/post/5bdec872e51d4551ee2761cb)

handler内存泄漏及解决办法：如果 Handler 不是静态内部类，Handler 会持有 Activity 的匿名引用。当 Activity 要被回收时，因为 Handler 在做耗时操作没有被释放，Handler Activity 的引用不能被释放导致 Activity 没有被回收停留在内存中造成内存泄露。    

解决方法是：1). 将 Handler 设为静态内部类；2). 使 Handler 持有 Activity 的弱引用；3). 在 Activity 生命周期 `onDestroy()` 中调用 `Handler.removeCallback()` 方法。

- **为什么不能在子线程中访问 UI？**

Android 中的控件不是线程安全的，之所以这样设计是为了：1).设计成同步的可以简化使用的复杂度；2).可以提升控件的性能（异步加锁在非多线程环境是额外的开销）。

- **Handler.post() 的逻辑在哪个线程执行的，是由 Looper 所在线程还是 Handler 所在线程决定的？**（这里的 Handler 所在的线程指的是调用 Handler 的 `post()` 方法时 Handler 所在的线程）
- **Handler 的 post()/send() 的原理？**
- **Handler 的 post() 和 postDelayed() 方法的异同？**

`post()` 方法所在的线程由 Looper 所在线程决定的；最终逻辑是在 `Looper.loop()` 方法中，从 MQ 中拿出 Message，并且执行其逻辑。这是在 Looper 中执行的。因此由 Looper 所在线程决定。

不论你调用 `send()` 类型的方法还是 `post()` 类型的方法，最终都会调用到 `sendMessageAtTime()` 方法。`post()` 和 `postDelay()` 的区别在于，前者使用当前时间，后者使用当前时间+delay 的时间来决定消息触发的时间。最终方法的参数都将被包装成一个 Message 对象加入到 Handler 对应的 Looper 的 MQ 中被执行。

- **Looper 和 Handler 一定要处于一个线程吗？子线程中可以用 MainLooper 去创建 Handler吗？**

Looper 和 Handler 不需要再一个线程中，默认的情况下会从 TL 中取当前线程对应的 Looper，但我们可以通过显式地指定一个 Looper 的方式来创建 Handler. 比如，当我们想要在子线程中发送消息到主线程中，那么我们可以

```java
Handler handler = new Handler(Looper.getMainLooper());
```

- **Handler.post() 方法发送的是同步消息吗？可以发送异步消息吗？**

用户层面发送的都是同步消息，不能发送异步消息；异步消息只能由系统发送。

- **MessageQueue.next() 会因为发现了延迟消息，而进行阻塞。那么为什么后面加入的非延迟消息没有被阻塞呢？**
- **MessageQueue.enqueueMessage() 方法的原理，如何进行线程同步的？**
- **MessageQueue.next() 方法内部的原理？**
- next() 是如何处理一般消息的？
- next() 是如何处理同步屏障的？
- next() 是如何处理延迟消息的？

调用 `MessageQueue.next()` 方法的时候会调用 Native 层的 `nativePollOnce()` 方法进行精准时间的阻塞。在 Native 层，将进入 `pullInner()` 方法，使用 `epoll_wait` 阻塞等待以读取管道的通知。如果没有从 Native 层得到消息，那么这个方法就不会返回。此时主线程会释放 CPU 资源进入休眠状态。

当我们加入消息的时候，会调用 `MessageQueue.enqueueMessage()` 方法，添加完 Message 后，如果消息队列被阻塞，则会调用 Native 层的 `nativeWake()` 方法去唤醒。它通过向管道中写入一个消息，结束上述阻塞，触发上面提到的 `nativePollOnce()` 方法返回，好让加入的 Message 得到分发处理。

 `MessageQueue.enqueueMessage()` 使用 synchronized 代码块去进行同步。

资料：[Android 中的 Handler 的 Native 层研究](https://www.liangzl.com/get-article-detail-14435.html)

- Handler 的 `dispatchMessage()` 分发消息的处理流程？

使用 Handler 的时候我们会覆写 Handler 的 `handleMessage()` 方法。当我们调用该 Handler 的 `send()` 或者 `post()` 发送一个消息的时候，发送的信息会被包装成 Message，并且将该 Message 的 target 指向当前 Handler，这个消息会被放进 Looper 的 MQ 中。然后在 Looper 的循环中，取出这个 Message，并调用它的 target Handler，也就是我们定义的 Handler 的 `dispatchMessage()` 方法处理消息，此时会调用到 Handler 的  `handleMessage()` 方法处理消息，并回调 Callback. 

- Handler 为什么要有 Callback 的构造方法？

当 Handler 在消息队列中被执行的时候会直接调用 Handler 的 `dispatchMessage()` 方法回调 Callback.

- Handler构造方法中通过 `Looper.myLooper()` 是如何获取到当前线程的 Looper 的？

从 TL 中获取

- **MessageQueue 中底层是采用的队列？**

是单链表，不是队列

- Looper 的两个退出方法？
- quit() 和 quitSafely() 有什么区别
- 子线程中创建了 Looper，在使用完毕后，终止消息循环的方法？
- quit() 和 quitSafely() 的本质是什么？

`quit()` 和 `quitSafely()` 的本质就是让消息队列的 `next()` 返回 null，以此来退出`Looper.loop()`。

`quit()` 调用后直接终止 Looper，不在处理任何 Message，所有尝试把 Message 放进消息队列的操作都会失败，比如 `Handler.sendMessage()` 会返回 false，但是存在不安全性，因为有可能有 Message 还在消息队列中没来的及处理就终止 Looper 了。

`quitSafely()` 调用后会在所有消息都处理后再终止 Looper，所有尝试把 Message 放进消息队列的操作也都会失败。

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
            nativeWake(mPtr);
        }
    }
```

- Looper.loop() 在什么情况下会退出？

1).`next()` 方法返回的 msg == null；2).线程意外终止。

- **Looper.loop() 的源码流程?**

1. 获取到 Looper 和消息队列；
2. for 无限循环，阻塞于消息队列的 `next()` 方法；
3. 取出消息后调用 `msg.target.dispatchMessage(msg)` 进行消息分发。

- `Looper.loop()` 方法执行时，如果内部的 `myLooper()` 获取不到Looper会出现什么结果?

异常

- Android 如何保证一个线程最多只能有一个 Looper？如何保证只有一个 MessageQueue

通过保证只有一个 Looper 来保证只有以一个 MQ. 在一个线程中使用 Handler 之前需要使用 `Looper.prepare()` 创建 Looper，它会从 TL 中获取，如果发现 TL 中已经存在 Looper，就抛异常。

- Handler 消息机制中，一个 Looper 是如何区分多个 Handler 的？

根据消息的分发机制，Looper 不会区分 Handler，每个 Handler 会被添加到 Message 的 target 字段上面，Looper 通过调用 `Message.target.handleMessage()` 来让 Handler 处理消息。
