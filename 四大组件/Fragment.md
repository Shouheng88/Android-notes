# 关于Fragment

## 1、Fragment的生命周期回调

![](http://img.my.csdn.net/uploads/201211/29/1354170699_6619.png)

从上图可以看出，Fragment的生命周期相比于Activity，在创建的过程中增加了onAttach()、onCreateView()和onActivityCreated()三个方法，在销毁的过程中增加了onDestroyView()和onDetach()两个方法。

下面是两者的对比：

![](http://img.my.csdn.net/uploads/201211/29/1354170682_3824.png)

生命周期回调的总结：

1. 生命周期调用：
	1. 切换到指定Fragment时：**A.onAttach()->A.onCreate()->A.onCreateView()->A.onActivityCreated()->A.onStart()->A.onResume()**。
	2. 接1，屏幕灭掉：**A.onPause()->A.onSaveInstanceState()->A.onStop()**。
	3. 接2，屏幕解锁：**A.onStart()->A.onResume()**。
	4. 接3，切换到其他Fragment:**A.onPause()->A.onStop()->A.onDestroyView()**。
	5. 接4，切换回Fragment **A.onCreateView()->A.onActivityCreated()->A.onStart()->A.onResume()**。
	6. 接5，回到桌面：**A.onPause()->A.onSaveInstanceState()->A.onStop()**。
	7. 接6，回到应用：**A.onStart()->A.onResume()**。
	8. 接7，退出应用：**A.onPause()->A.onStop()->A.onDestroyView()->A.onDestroy()->A.onDetach()**。

参考：

1. [理解Fragment生命周期](http://blog.csdn.net/forever_crying/article/details/8238863/)

