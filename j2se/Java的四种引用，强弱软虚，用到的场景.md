## Java的四种引用，强软弱虚，用到的场景

Java在JDK1.2之后引入了强引用、软引用、弱引用和虚引用4种概念，来表示引用的状态。四种引用强度依次减弱。

### 1、强引用（StrongReference）

强引用不会被GC回收，当使用

    Object obj = new Object();

声明一个对象的时候，这里的obj引用便是一个强引用。如果一个对象是强引用，那垃圾回收器绝不会回收它。当内存空 间不足，Java虚拟机宁愿抛出OutOfMemoryError错误，使程序异常终止，也不会靠随意回收具有强引用的对象来解决内存不足问题。

如果不使用时，要通过如下方式来弱化引用，如下：

    o = null; // 帮助垃圾收集器回收此对象

### 2、软引用（SoftReference）

如果一个对象只具有软引用，则内存空间足够，垃圾回收器就不会回收它；如果内存空间不足了，就会回收这些对象的内存。只要垃圾回收器没有回收它，该对象就可以被程序使用。软引用可用来实现内存敏感的高速缓存。

    String str = new String("abc");                               // 强引用
    SoftReference<String> softRef=new SoftReference<String>(str); // 软引用

### 3、弱引用（WeakReference）

弱引用与软引用的区别在于：只具有弱引用的对象拥有更短暂的生命周期。在垃圾回收器线程扫描它所管辖的内存区域的过程中，**一旦发现了只具有弱引用的对象，不管当前内存空间足够与否，都会回收它的内存**。不过，由于垃圾回收器是一个优先级很低的线程，因此不一定会很快发现那些只具有弱引用的对象。 

    String str = new String("abc");    
    WeakReference<String> abcWeakRef = new WeakReference<String>(str);

用法，比如Java API中的WeakHashMap，其中的Entry，即哈希表中的元素，的定义方式是：

    private static class Entry<K,V> extends WeakReference<Object> implements Map.Entry<K,V> {}
 
### 4、虚引用（PhantomReference）

“虚引用”顾名思义，就是形同虚设，虚引用并不会决定对象的生命周期。如果一个对象仅持有虚引用，那么它就和没有任何引用一样，**在任何时候都可能被垃圾回收器回收**。

-----

### 更多

更多关于JVM的内容参考：[https://github.com/Shouheng88/Java-Programming/tree/master/JVM](https://github.com/Shouheng88/Java-Programming/tree/master/JVM)

