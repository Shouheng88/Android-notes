# Android 基础回顾：Broadcast 基础

## 1、关于广播

广播是 Android 提供的一种全局通信机制。

### 1.1 分类

1. 按照注册方式：**静态注册和动态注册**两种；
2. 按照作用范围：**本地广播和普通广播**两种，普通广播是全局的，所有应用程序都可以接收到，容易会引起安全问题。本地广播只能够在应用内传递，广播接收器也只能接收应用内发出的广播；
3. 按照是否有序：**有序广播和无序广播**两种，无序广播各接收器接收的顺序无法确定，并且在广播发出之后接收器只能接收，不能拦截和进行其他处理，两者的区别主要体现在发送时调用的方法上。

### 1.2 实现

#### 1.2.1 静态广播

注册，这里的 StaticBroadcastReceiver 是自定义类：

```xml
<receiver android:name=".StaticBroadcastReceiver">
    <intent-filter>
        <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
    </intent-filter>
</receiver>
```

我们可以将要实现的逻辑放在这个类的方法中进行执行：

```java
public class StaticBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Do something
	}
}
```

需要注意 Andrdoid 8.0 之后系统对广播进行了一些限制（[官方文档](https://developer.android.google.cn/about/versions/oreo/android-8.0)），具体地：

1. 在 Android 8.0 的平台上，应用不能对大部分的广播进行静态注册，也就是说，不能在AndroidManifest 文件对**有些**广播进行静态注册（注意“有些”，因为不是所有的广播都不能注册）。
2. 当程序运行在后台的时候，静态广播中不能启动服务。比如之前实现闹钟的时候是监听时间变化来实现的，在 8.0 之后就会抛出异常。

解决方式是使用动态注册方式（一般情况下使用动态注册就好了）。

#### 1.2.2 动态广播

与静态广播相似，但是不需要在 Manifest 中进行注册。

```java
    // 监听广播：一般在 Activity 的 onCreate() 方法中注册
    netWorkChangReceiver = new StaticBroadcastReceiver();
    IntentFilter filter = new IntentFilter();
    filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    registerReceiver(netWorkChangReceiver, filter);

    // 取消监听：然后在 Activity 的 onDestroy() 中取消注册
    unregisterReceiver(netWorkChangReceiver);
```

**注意当页面被销毁的时候需要取消注册广播！**

#### 1.2.3 本地广播

本地广播的核心类是 LocalBroadcastManager，使用它的静态方法 `getInstance()` 获取一个单例之后就可以使用该单例的 `registerReceiver()`、`unregisterReceiver()` 和 `sendBroadcast()` 等方法来进行操作了。

```java
    // 获取单例
    localBroadcastManager = LocalBroadcastManager.getInstance(this);

    // 注册广播
    IntentFilter filter = new IntentFilter();
    filter.addAction("me.shouheng.MyBroadcastReceiver");
    localReceiver = new LocalReceiver();
    localBroadcastManager.registerReceiver(localReceiver, filter);

    // 发送广播
    Intent intent = new Intent("me.shouheng.MyBroadcastReceiver");
    localBroadcastManager.sendBroadcast(intent);

    // 取消注册
    localBroadcastManager.unregisterReceiver(localReceiver);
```

#### 1.2.4 有序广播

在 xml 中进行注册的时候通过 `android:priority` 指定一个范围在 -1000~1000 之间的整数来指定广播的接收顺序。优先级高的会先接收到，优先级相等的话则顺序不确定。并且前面的广播可以在方法中向 Intent 写入数据，后面的广播可以接收到写入的值。

```xml
    <receiver android:name=".MyReceiver_1">
        <intent-filter android:priority="200">
            <action android:name="com.song.123"/>
        </intent-filter>
    </receiver>
    <receiver android:name=".MyReceiver_2">
        <intent-filter android:priority="1000">
            <action android:name="com.song.123"/>
        </intent-filter>
    </receiver>
```



