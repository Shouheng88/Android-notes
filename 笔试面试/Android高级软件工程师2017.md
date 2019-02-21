链接们：

[11](https://www.jianshu.com/p/a2bbd8565a64)    

[22](https://blog.csdn.net/WHB20081815/article/details/74436204)

[android面试题-与IPC机制相关面试题](https://blog.csdn.net/mwq384807683/article/details/70313632)

[分享一份非常强势的Android面试题](http://www.jcodecraeer.com/plus/view.php?aid=12303)    

[Android面试一天一题](https://blog.csdn.net/wo_ha/article/details/79729873)

[2018年Android面试题含答案--适合中高级（上）](https://www.cnblogs.com/huangjialin/p/8657565.html)

[Android-Interview](https://github.com/android-exchange/Android-Interview)










- [ ] 画出 Android 的大体架构图
- [ ] 描述清点击 Android Studio 的 build 按钮后发生了什么
- [ ] 大体说清一个应用程序安装到手机上时发生了什么
- [ ] 对 Dalvik、ART 虚拟机有基本的了解
- [ ] App 是如何沙箱化，为什么要这么做
- [ ] 权限管理系统（底层的权限是如何进行 grant 的）
- [ ] 进程和 Application 的生命周期
- [ ] 系统启动流程 Zygote进程 –> SystemServer进程 –> 各种系统服务 –> 应用进程
- [ ] recycleview listview 的区别,性能
- [ ] 进程调度
- [ ] 线程和进程的区别？
- [ ] 动态权限适配方案，权限组的概念
- [ ] 图片加载库相关，bitmap 如何处理大图，如一张 30M 的大图，如何预防 OOM
- [ ] 进程保活
- [ ] 广播（动态注册和静态注册区别，有序广播和标准广播）
- [ ] listview 图片加载错乱的原理和解决方案
- [ ] service 生命周期
- [ ] 数据库数据迁移问题
- [ ] 是否熟悉 Android jni 开发，jni 如何调用 java 层代码
- [ ] 计算一个 view 的嵌套层级
- [ ] 项目组件化的理解
- [ ] Android 系统为什么会设计 ContentProvider，进程共享和线程安全问题
- [ ] Android 相关优化（如内存优化、网络优化、布局优化、电量优化、业务优化） 
- [ ] EventBus 实现原理
- [ ] 四大组件
- [ ] Android 中数据存储方式
- [ ] ActicityThread 相关？
- [ ] Android 中进程内存的分配，能不能自己分配定额内存
- [x] ViewPager 使用细节，如何设置成每次只初始化当前的 Fragment，其他的不初始化

    不能使用 ViewPager.setOffscreenPageLimit(0)，其最小值为1. 

- [ ] ListView 重用的是什么
- [ ] 应用安装过程
- [ ] fragment 之间传递数据的方式？
- [ ] OOM 的可能原因？
- [ ] 为什么要有线程，而不是仅仅用进程？
- [ ] 内存泄漏的可能原因？
- [ ] 用 IDE 如何分析内存泄漏？
- [ ] 触摸事件的分发？
- [ ] 简述 Activity 启动全部过程？
- [ ] 性能优化如何分析 systrace？
- [ ] 广播的分类？
- [ ] 点击事件被拦截，但是相传到下面的 view，如何操作？
- [ ] 如何保证多线程读写文件的安全？
- [ ] Activity 启动模式
- [ ] 广播的使用方式，场景
- [ ] App 中唤醒其他进程的实现方式
- [ ] Android 中开启摄像头的主要步骤
- [ ] Activity 生命周期
- [ ] AlertDialog, popupWindow, Activity 区别
- [ ] fragment 各种情况下的生命周期
- [ ] Activity 上有 Dialog 的时候按 home 键时的生命周期
- [ ] 横竖屏切换的时候，Activity 各种情况下的生命周期
- [ ] Application 和 Activity 的 context 对象的区别
- [ ] ANR 怎么分析解决
- [x] LinearLayout、RelativeLayout、FrameLayout 的特性、使用场景

    ...

- [ ] 如何实现 Fragment 的滑动
- [ ] AndroidManifest 的作用与理解
- [ ] Jni 用过么？
- [ ] 多进程场景遇见过么？
- [ ] sqlite 升级，增加字段的语句
- [ ] bitmap recycler 相关
- [ ] Activity 与 Fragment 之间生命周期比较
- [ ] 广播的使用场景
- [ ] Bitmap 使用时候注意什么？
- [ ] Oom 是否可以 try catch ？

内存优化    

- [ ] ListView 的优化
- [ ] Android 进程分类
- [ ] 前台切换到后台，然后再回到前台，Activity 生命周期回调方法。弹出 Dialog，生命值周期回调方法。
- [ ] RecycleView的 使用，原理，RecycleView 优化




## 算法

- [ ] 排序，快速排序的实现
- [ ] 树：B 树、B+ 树的介绍
- [ ] 图：有向无环图的解释
- [ ] 二叉树 深度遍历与广度遍历
- [ ] 常用数据结构简介
- [ ] 判断环（猜测应该是链表环）
- [ ] 排序，堆排序实现
- [ ] 链表反转
- [ ] x 个苹果，一天只能吃一个、两个、或者三个，问多少天可以吃完
- [ ] 堆排序过程，时间复杂度，空间复杂度
- [ ] 快速排序的时间复杂度，空间复杂度
- [ ] 翻转一个单项链表
- [ ] 两个不重复的数组集合中，求共同的元素
- [ ] 上一问扩展，海量数据，内存中放不下，怎么求出
- [ ] 合并多个单有序链表（假设都是递增的）
- [ ] 算法判断单链表成环与否？
- [ ] 二叉树，给出根节点和目标节点，找出从根节点到目标节点的路径
- [ ] 一个无序，不重复数组，输出 N 个元素，使得 N 个元素的和相加为 M，给出时间复杂度、空间复杂度。手写算法
- [ ] 数据结构中堆的概念，堆排序

常见的算法题！！！！


## 设计模式

- [ ] 设计模式相关（例如Android中哪里使用了观察者模式，单例模式相关）
- [ ] MVP模式
- [ ] Java设计模式，观察者模式
- [ ] 模式MVP，MVC介绍

参考：

1. 整理自：[知乎 Misssss Cathy 的回答](https://zhuanlan.zhihu.com/p/30016683)


深度研究：

1. SurefaceView, TextureView, Camera
2. RecyclerView
3. Adapter + Fragment

热修补+插件化（组件化）

PMW WMS AMW 相关的东西

优化经验：

1. ANR 处理
2. 相机优化
3. RV 优化
4. 其他的优化
5. 逻辑优化

## 项目相关

以上的深度研究 + 屏幕适配方式 + WorkManager 的研究

权限机制的底层原理