# Activity

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
