# EventBus 的使用

## 1、EventBus 简介

EventBus是一种用于Android的事件发布-订阅总线，由GreenRobot开发，Gihub地址是：[EventBus](https://github.com/greenrobot/EventBus)。它简化了应用程序内各个组件之间进行通信的复杂度，尤其是碎片之间进行通信的问题，可以避免由于使用广播通信而带来的诸多不便。

### 1.1 三个角色

1. **Event**：事件，它可以是任意类型，EventBus会根据事件类型进行全局的通知。
2. **Subscriber**：事件订阅者，在EventBus 3.0之前我们必须定义以onEvent开头的那几个方法，分别是`onEvent`、`onEventMainThread`、`onEventBackgroundThread`和`onEventAsync`，而在3.0之后事件处理的方法名可以随意取，不过需要加上注解`@subscribe`，并且指定线程模型，默认是`POSTING`。
3. **Publisher**：事件的发布者，可以在任意线程里发布事件。一般情况下，使用`EventBus.getDefault()`就可以得到一个EventBus对象，然后再调用`post(Object)`方法即可。

### 1.2 四种线程模型

EventBus3.0有四种线程模型，分别是：

1. **POSTING**：默认，表示事件处理函数的线程跟发布事件的线程在同一个线程。
2. **MAIN**：表示事件处理函数的线程在主线程(UI)线程，因此在这里不能进行耗时操作。
3. **BACKGROUND**：表示事件处理函数的线程在后台线程，因此不能进行UI操作。如果发布事件的线程是主线程(UI线程)，那么事件处理函数将会开启一个后台线程，如果果发布事件的线程是在后台线程，那么事件处理函数就使用该线程。
4. **ASYNC**：表示无论事件发布的线程是哪一个，事件处理函数始终会新建一个子线程运行，同样不能进行UI操作。

## 2、EventBus 使用

### 2.1 引入依赖

在使用之前先要引入如下依赖：

    implementation 'org.greenrobot:eventbus:3.1.1'

### 2.2 定义事件

然后，我们定义一个事件的封装对象。在程序内部就使用该对象作为通信的信息：

```
public class MessageWrap {

    public final String message;

    public static MessageWrap getInstance(String message) {
        return new MessageWrap(message);
    }

    private MessageWrap(String message) {
        this.message = message;
    }
}
```

### 2.3 发布事件

然后，我们定义一个Activity：

```
@Route(path = BaseConstants.LIBRARY_EVENT_BUS_ACTIVITY1)
public class EventBusActivity1 extends CommonActivity<ActivityEventBus1Binding> {

    @Override
    protected void doCreateView(Bundle savedInstanceState) {
        // 为按钮添加添加单击事件
        getBinding().btnReg.setOnClickListener(v -> EventBus.getDefault().register(this));
        getBinding().btnNav2.setOnClickListener( v ->
                ARouter.getInstance()
                        .build(BaseConstants.LIBRARY_EVENT_BUS_ACTIVITY2)
                        .navigation());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGetMessage(MessageWrap message) {
        getBinding().tvMessage.setText(message.message);
    }
}
```

这里我们当按下按钮的时候向EventBus注册监听，然后按下另一个按钮的时候跳转到拎一个Activity，并在另一个Activity发布我们输入的事件。在上面的Activity中，我们会添加一个监听的方法，即`onGetMessage`，这里我们需要为其加入注解`Subscribe`并指定线程模型为主线程`MAIN`。最后，就是在Activity的`onDestroy`方法中取消注册该Activity。

下面是另一个Activity的定义，在这个Activity中，我们当按下按钮的时候从EditText中取出内容并进行发布，然后我们退出到之前的Activity，以测试是否正确监听到发布的内容。

```
@Route(path = BaseConstants.LIBRARY_EVENT_BUS_ACTIVITY2)
public class EventBusActivity2 extends CommonActivity<ActivityEventBus2Binding> {

    @Override
    protected void doCreateView(Bundle savedInstanceState) {
        getBinding().btnPublish.setOnClickListener(v -> publishContent());
    }

    private void publishContent() {
        String msg = getBinding().etMessage.getText().toString();
        EventBus.getDefault().post(MessageWrap.getInstance(msg));
        ToastUtils.makeToast("Published : " + msg);
    }
}
```

根据测试的结果，我们的确成功地接收到了发送的信息。

### 2.4 黏性事件

所谓的黏性事件，就是指发送了该事件之后再订阅者依然能够接收到的事件。使用黏性事件的时候有两个地方需要做些修改。一个是订阅事件的地方，这里我们在先打开的Activity中注册监听黏性事件：

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onGetStickyEvent(MessageWrap message) {
        String txt = "Sticky event: " + message.message;
        getBinding().tvStickyMessage.setText(txt);
    }

另一个是发布事件的地方，这里我们在新的开的Activity中发布黏性事件。即调用EventBus的`postSticky`方法来发布事件：

    private void publishStickyontent() {
        String msg = getBinding().etMessage.getText().toString();
        EventBus.getDefault().postSticky(MessageWrap.getInstance(msg));
        ToastUtils.makeToast("Published : " + msg);
    }

按照上面的模式，我们先在第一个Activity中打开第二个Activity，然后在第二个Activity中发布黏性事件，并回到第一个Activity注册EventBus。根据测试结果，当按下注册按钮的时候，会立即触发上面的订阅方法从而获取到了黏性事件。

### 2.5 优先级

在`Subscribe`注解中总共有3个参数，上面我们用到了其中的两个，这里我们使用以下第三个参数，即`priority`。它用来指定订阅方法的优先级，是一个整数类型的值，默认是0，值越大表示优先级越大。在某个事件被发布出来的时候，优先级较高的订阅方法会首先接受到事件。

为了对优先级进行测试，这里我们需要对上面的代码进行一些修改。这里，我们使用一个布尔类型的变量来判断是否应该取消事件的分发。我们在一个较高优先级的方法中通过该布尔值进行判断，如果未`true`就停止该事件的继续分发，从而通过低优先级的订阅方法无法获取到事件来证明优先级较高的订阅方法率先获取到了事件。

这里有几个地方需要**注意**：

1. 只有当两个订阅方法使用相同的`ThreadMode`参数的时候，它们的优先级才会与`priority`指定的值一致；
2. 只有当某个订阅方法的`ThreadMode`参数为`POSTING`的时候，它才能停止该事件的继续分发。

所以，根据以上的内容，我们需要对代码做如下的调整：

    // 用来判断是否需要停止事件的继续分发
    private boolean stopDelivery = false;

    @Override
    protected void doCreateView(Bundle savedInstanceState) {
        // ...

        getBinding().btnStop.setOnClickListener(v -> stopDelivery = true);
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 0)
    public void onGetMessage(MessageWrap message) {
        getBinding().tvMessage.setText(message.message);
    }

    // 订阅方法，需要与上面的方法的threadMode一致，并且优先级略高
    @Subscribe(threadMode = ThreadMode.POSTING, sticky = true, priority = 1)
    public void onGetStickyEvent(MessageWrap message) {
        String txt = "Sticky event: " + message.message;
        getBinding().tvStickyMessage.setText(txt);
        if (stopDelivery) {
            // 终止事件的继续分发
            EventBus.getDefault().cancelEventDelivery(message);
        }
    }

即我们在之前的代码之上增加了一个按钮，用来将`stopDelivery`的值置为`true`。该字段随后将会被用来判断是否要终止事件的继续分发，因为我们需要在代码中停止事件的继续分发，所以，我们需要将上面的两个订阅方法的`threadMode`的值都置为`ThreadMode.POSTING`。

按照，上面的测试方式，首先我们在当前的Activity注册监听，然后跳转到另一个Activity，发布事件并返回。第一次的时候，这里的两个订阅方法都会被触发。然后，我们按下停止分发的按钮，并再次执行上面的逻辑，此时只有优先级较高的方法获取到了事件并将该事件终止。

## 总结

上面的内容是EventBus的基本使用方法，相关的源码参考：[Github](https://github.com/Shouheng88/Android-references/tree/master/libraries/src/main/java/me/shouheng/libraries/eventbus)

