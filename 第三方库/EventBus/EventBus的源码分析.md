# Android EventBus 的源码解析

在这篇文章中，我会注重分析Android中的EventBus的实现原理，如果你之前还没有使用过该框架，那么可以参考我的另一篇文章来了解如何使用EventBus：[Android EventBus 的使用](https://juejin.im/post/5b66df92e51d45348813ae3f)。
此外，我还分析过Google的Guava中的EventBus的实现原理，如果想了解这方面的内容可以参考：[Guava源码分析—EventBus](https://juejin.im/post/5b61c852e51d451956055476)。

## 1、源码分析

在分析EventBus源码的时候，我们先从获取一个EventBus实例的方法入手，然后再分别看一下它的注册、取消注册、发布事件以及触发观察方法的代码是如何实现的。在下面的文章中我们将会回答以下几个问题：

1. 在EventBus中，使用`@Subscribe`注解的时候指定的`ThreadMode`是如何实现在不同线程间传递数据的？
2. 使用注解和反射的时候的效率问题，是否会像Guava的EventBus一样有缓存优化？
3. 黏性事件是否是通过内部维护了之前发布的数据来实现的，是否使用了缓存？

### 1.1 获取实例

在创建EventBus实例的时候，一种方式是按照我们上面的形式，通过EventBus的静态方法`getDefault`来获取一个实例。`getDefault`本身会调用其内部的构造方法，通过传入一个默认的`EventBusBuilder`来创建EventBus。此外，我们还可以直接通过EventBus的`builder()`方法获取一个`EventBusBuilder`的实例，然后通过该构建者模式来个性化地定制自己的EventBus。即：

    // 静态的单例实例
    static volatile EventBus defaultInstance;

    // 默认的构建者
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();

    // 实际上使用了DCL双检锁机制，这里简化了一下
    public static EventBus getDefault() {
        if (defaultInstance == null) defaultInstance = new EventBus();
        return defaultInstance;
    }

    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    // 调用getDefault的时候，最终会调用该方法，使用DEFAULT_BUILDER创建一个实例
    EventBus(EventBusBuilder builder) {
        // ...
    }

    // 也可以使用下面的方法获取一个构建者，然后使用它来个性化定制EventBus
    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

### 1.2 注册

当调用EventBus实例的`register`方法的时候，会执行下面的逻辑：

    public void register(Object subscriber) {
        // 首席会获取注册的对象的类型
        Class<?> subscriberClass = subscriber.getClass();
        // 然后获取注册的对象的订阅方法
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        // 对当前实例加锁，并不断执行监听的逻辑
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                // 对订阅方法进行注册
                subscribe(subscriber, subscriberMethod);
            }
        }
    }

这里的`SubscriberMethod`封装了订阅方法（使用`@Subscribe`注解的方法）类型的信息，它的定义如下所示。从下面可以的代码中我们可以看出，实际上该类就是通过几个字段来存储`@Subscribe`注解中指定的类型信息，以及一个方法的类型变量。

```
public class SubscriberMethod {
    final Method method;
    final ThreadMode threadMode;
    final Class<?> eventType;
    final int priority;
    final boolean sticky;

    // ...
}
```

`register`方法通过`subscriberMethodFinder`实例的`findSubscriberMethods`方法来获取该观察者类型中的所有订阅方法，然后将所有的订阅方法分别进行订阅。下面我们先看下查找订阅者的方法。

#### 查找订阅者的订阅方法

下面是`SubscriberMethodFinder`中的`findSubscriberMethods`方法：

    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        // 这里首先从缓存当中尝试去取该订阅者的订阅方法
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        // 当缓存中没有找到该观察者的订阅方法的时候使用下面的两种方法获取方法信息
        if (ignoreGeneratedIndex) {
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException(...);
        } else {
            // 将获取到的订阅方法放置到缓存当中
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

这里我们先从缓存当中尝试获取某个观察者中的所有订阅方法，如果没有可用缓存的话就从该类中查找订阅方法，并在返回结果之前将这些方法信息放置到缓存当中。这里的`ignoreGeneratedIndex`参数表示是否忽略注解器生成的`MyEventBusIndex`，该值默认为`false`。然后，我们会进入到下面的方法中获取订阅方法信息：

    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // 这里通过FindState对象来存储找到的方法信息
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        // 这里是一个循环操作，会从当前类开始遍历该类的所有父类
        while (findState.clazz != null) {
            // 获取订阅者信息
            findState.subscriberInfo = getSubscriberInfo(findState); // 1
            if (findState.subscriberInfo != null) {
                // 如果使用了MyEventBusIndex，将会进入到这里并获取订阅方法信息
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                // 未使用MyEventBusIndex将会进入这里使用反射获取方法信息
                findUsingReflectionInSingleClass(findState); // 2
            }
            // 将findState.clazz设置为当前的findState.clazz的父类
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

在上面的代码中，会从当前订阅者类开始直到它最顶层的父类进行遍历来获取订阅方法信息。这里在循环的内部会根据我们是否使用了`MyEventBusIndex`走两条路线，对于我们没有使用它的，会直接使用反射来获取订阅方法信息，即进入2处。

下面是使用反射从订阅者中得到订阅方法的代码：

    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            // 获取该类中声明的所有方法
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            methods = findState.clazz.getMethods();
            findState.skipSuperClasses = true;
        }
        // 对方法进行遍历判断
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            // 这里会对方法的修饰符进行校验
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                // 这里对方法的输入参数进行校验
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1) {
                    // 获取方法的注解，用来从注解中获取注解的声明信息
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null) {
                        // 获取该方法的第一个参数
                        Class<?> eventType = parameterTypes[0];
                        if (findState.checkAdd(method, eventType)) {
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            // 最终将封装之后的方法塞入到列表中
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException(...);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(...);
            }
        }
    }

这里会对当前类中声明的所有方法进行校验，并将符合要求的方法的信息封装成一个`SubscriberMethod`对象塞到列表中。

#### 注册订阅方法

直到了如何拿到所有的订阅方法之后，我们回到之前的代码，看下订阅过程中的逻辑：

    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        // 将所有的观察者和订阅方法封装成一个Subscription对象
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod); // 1
        // 尝试从缓存中根据事件类型来获取所有的Subscription对象
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType); // 2
        if (subscriptions == null) {
            // 指定的事件类型没有对应的观察对象的时候
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException(...);
            }
        }

        // 这里会根据新加入的方法的优先级决定插入到队列中的位置
        int size = subscriptions.size(); // 2
        for (int i = 0; i <= size; i++) {
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        // 这里又会从“订阅者-事件类型”列表中尝试获取该订阅者对应的所有事件类型
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber); // 3
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);

        // 如果是黏性事件还要进行如下的处理
        if (subscriberMethod.sticky) { // 4
            if (eventInheritance) {
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        // 这里会向该观察者通知所有的黏性事件
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

这里涉及到了几个集合，它们是用来做缓存的，还有就是来维护观察者、事件类型和订阅方法之间的关系的。注册观察的方法比较长，我们可以一点一点来看。首先，会在代码1处将观察者和订阅方法封装成一个`Subscription`对象。然后，在2处用到了`CopyOnWriteArrayList`这个集合，它是一种适用于**多读写少**场景的数据结构，是一种线程安全的数组型的数据结构，主要用来存储一个事件类型所对应的全部的`Subscription`对象。EventBus在这里通过一个`Map<Class<?>, CopyOnWriteArrayList<Subscription>> `类型的哈希表来维护这个映射关系。然后，我们的程序执行到2处，在这里会对`Subscription`对象的列表进行遍历，并根据订阅方法的优先级，为当前的`Subscription`对象寻找一个合适的位置。3的地方主要的逻辑是获取指定的观察者对应的全部的观察事件类型，这里也是通过一个哈希表来维护这种映射关系的。然后，在代码4处，程序会根据当前的订阅方法是否是黏性的，来决定是否将当前缓存中的信息发送给新订阅的方法。这里会通过`checkPostStickyEventToSubscription`方法来发送信息，它内部的实现的逻辑和`post`方法类似，我们不再进行说明。

取消注册的逻辑比较比较简单，基本上就是注册操作反过来——将当前订阅方法的信息从缓存中踢出来，我们不再进行分分析。下面我们分析另一个比较重要的地方，即发送事件相关的逻辑。

### 1.3 通知

通知的逻辑相对来说会比较复杂一些，因为这里面涉及一些线程之间的操作。我们看下下面的代码吧：

    public void post(Object event) {
        // 这里从线程局部变量中取出当前线程的状态信息
        PostingThreadState postingState = currentPostingThreadState.get();
        // 这里是以上线程局部变量内部维护的一个事件队列
        List<Object> eventQueue = postingState.eventQueue;
        // 将当前要发送的事件加入到队列中
        eventQueue.add(event);

        if (!postingState.isPosting) {
            postingState.isMainThread = isMainThread();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                // 不断循环来发送事件
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingState); // 1
                }
            } finally {
                // 恢复当前线程的信息
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

这里的`currentPostingThreadState`是一个`ThreadLocal`类型的变量，其中存储了对应于当前线程的`PostingThreadState`对象，该对象中存储了当前线程对应的事件列表和线程的状态信息等。从上面的代码中可以看出，`post`方法会在1处不断从当前线程对应的队列中取出事件并进行发布。下面我们看以下这里的`postSingleEvent`方法。

    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            // 这里向上查找该事件的所有父类
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                // 对上面的事件进行处理
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        // 找不到该事件的异常处理
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent 
                    && eventClass != NoSubscriberEvent.class 
                    && eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

在上面的代码中，我们会根据`eventInheritance`的值决定是否要同时遍历当前事件的所有父类的事件信息并进行分发。如果设置为`true`就将执行这一操作，并最终使用`postSingleEventForEventType`对每个事件类型进行处理。

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        // 获取指定的事件对应的所有的观察对象
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            // 遍历观察对象，并最终执行事件的分发操作
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

在上面的代码中，我们会通过传入的事件类型到缓存中取寻找它对应的全部的`Subscription`，然后对得到的`Subscription`列表进行遍历，并依次调用`postToSubscription`方法执行事件的发布操作。下面是`postToSubscription`方法的代码，这里我们会根据订阅方法指定的`threadMode`信息来执行不同的发布策略。

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case MAIN_ORDERED:
                if (mainThreadPoster != null) {
                    mainThreadPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException(...);
        }
    }

在上面的方法中，会根据当前的线程状态和订阅方法指定的`threadMode`信息来决定合适触发方法。这里的`invokeSubscriber`会在当前线程中立即调用反射来触发指定的观察者的订阅方法。否则会根据具体的情况将事件加入到不同的队列中进行处理。这里的`mainThreadPoster`最终继承自`Handler`，当调用它的`enqueue`方法的时候，它会发送一个事件并在它自身的`handleMessage`方法中从队列中取值并进行处理，从而达到在主线程中分发事件的目的。这里的`backgroundPoster`实现了`Runnable`接口，它会在调用`enqueue`方法的时候，拿到EventBus的`ExecutorService`实例，并使用它来执行自己。在它的`run`方法中会从队列中不断取值来进行执行。

## 总结

以上就是Android中的EventBus的源码分析，这里我们回答之前提出的几个问题来作结：

1. 在EventBus中，使用`@Subscribe`注解的时候指定的`ThreadMode`是如何实现在不同线程间传递数据的？

要求主线程中的事件通过`Handler`来实现在主线程中执行，非主线程的方法会使用EventBus内部的`ExecutorService`来执行。实际在触发方法的时候会根据当前线程的状态和订阅方法的`ThreadMode`指定的线程状态来决定何时触发方法。非主线程的逻辑会在`post`的时候加入到一个队列中被随后执行。

2. 使用注解和反射的时候的效率问题，是否会像Guava的EventBus一样有缓存优化？

内部使用了缓存，确切来说就是维护了一些映射的关系。但是它的缓存没有像Guava一样使用软引用之类方式进行优化，即一直是强引用类型的。

3. 黏性事件是否是通过内部维护了之前发布的数据来实现的，是否使用了缓存？

黏性事件会通过EventBus内部维护的一个`事件类型-黏性事件`的哈希表存储，当注册一个观察者的时候，如果发现了它内部有黏性事件监听，会执行`post`类似的逻辑将事件立即发送给该观察者。
