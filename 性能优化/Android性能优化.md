# Android 性能优化：打造高性能 App

## 2、绘制优化

### 2.1 onDraw() 方法

**onDraw() 方法会被频繁调用，因此不应该在其中做耗时逻辑和声明对象**。

## 3、内存泄漏

### 3.1 使用 LeakCanary

Square 公司开源的用于检测内存泄漏的库，Github 地址：[LeakCanary](https://github.com/square/leakcanary). 它的配置和使用比较简单，不介绍。

### 3.2 常见的内存泄漏：静态变量

一般人不会犯的错误，即将 View、Activity 和 Context 等类型的变量设置为静态的。因为静态变量的声明周期同整个应用，所以导致其所引用的 Context 无法被释放，致泄漏。某个类的生命周期确实应该与应用的生命周期一致的情况除外。

### 3.3 常见的内存泄漏：单例引用

比如在使用观察者模式的时候，使用静态的列表存储观察者，而观察者引用了 Context，会导致 Context 需要被销毁的时候，因为这里的引用而无法被释放。所以，**使用观察者和单例的时候，如果对象与 Context 相关，那么应该注意适当的时候释放引用**。

### 3.4 常见的内存泄漏：属性动画

从属性动画延申到**开线程并且线程中引用到了 Context 的情景**。因为 Context 需要被销毁的时候，线程或者动画可能还没有执行完毕，因而导致内存泄漏。

解决方案：弱引用或者在合适的位置和时机释放引用。

## 其他

### 图片使用

### 数据结构

**合理选择数据结构**：根据具体应用场景选择 LinkedList 和 ArrayList，比如 Adapter 中查找比增删要多，因此建议选择 ArrayList. 

**使用优化过的数据集合**：如 `SparseArray`、`SparseBooleanArray`等来替换 HashMap。因为 HashMap 的键必须是对象，而对象比数值类型需要多占用非常多的空间。

**少使用枚举**：枚举可以合理组织数据结构，但是枚举是对象，比普通的数值类型需要多使用很多空间。

**使用 static final 修饰常量**：

```java
static final int intVal = 42;  
static final String strVal = "Hello, world!";  
```

因为常量会在 dex 文件的初始化器当中进行初始化。当我们调用 intVal 时可以直接指向 42 的值，而调用 strVal 会用一种相对轻量级的字符串常量方式，而不是字段搜寻的方式。这种优化方式只对基本数据类型以及 String 类型的常量有效，对于其他数据类型的常量无效。

**合理使用数据结构**：比如 `android.util` 下面的 `Pair<F, S>`，在希望某个方法返回的数据恰好是两个的时候可以使用。显然，这种返回方式比返回数组或者列表含义清晰得多。延申一下：**有时候合理使用数据结构或者使用自定义数据结构，能够起到化腐朽为神奇的作用**。

**多线程**：不要开太多线程，如果小任务很多建议使用线程池或者 AsyncTask，建议直接使用 RxJava 来实现多线程，可读性和性能更好。




### 1.合理管理内存

### 2.节制的使用Service

如果应用程序需要使用Service来执行后台任务的话，只有当任务正在执行的时候才应该让Service运行起来。当启动一个Service时，系统会倾向于将这个Service所依赖的进程进行保留，系统可以在LRUcache当中缓存的进程数量也会减少，导致切换程序的时候耗费更多性能。我们可以使用IntentService，当后台任务执行结束后会自动停止，避免了Service的内存泄漏。

### 3.当界面不可见时释放内存

当用户打开了另外一个程序，我们的程序界面已经不可见的时候，我们应当将所有和界面相关的资源进行释放。重写Activity的onTrimMemory()方法，然后在这个方法中监听TRIM_MEMORY_UI_HIDDEN这个级别，一旦触发说明用户离开了程序，此时就可以进行资源释放操作了。

### 4.当内存紧张时释放内存

onTrimMemory()方法还有很多种其他类型的回调，可以在手机内存降低的时候及时通知我们，我们应该根据回调中传入的级别来去决定如何释放应用程序的资源。

### 5.避免在Bitmap上浪费内存

读取一个Bitmap图片的时候，千万不要去加载不需要的分辨率。可以压缩图片等操作。

### 7.知晓内存的开支情况

使用枚举通常会比使用静态常量消耗两倍以上的内存，尽可能不使用枚举
任何一个Java类，包括匿名类、内部类，都要占用大概500字节的内存空间
任何一个类的实例要消耗12-16字节的内存开支，因此频繁创建实例也是会在一定程序上影响内存的。使用HashMap时，即使你只设置了一个基本数据类型的键，比如说int，但是也会按照对象的大小来分配内存，大概是32字节，而不是4字节，因此最好使用优化后的数据集合

### 8.谨慎使用抽象编程

在Android使用抽象编程会带来额外的内存开支，因为抽象的编程方法需要编写额外的代码，虽然这些代码根本执行不到，但是也要映射到内存中，不仅占用了更多的内存，在执行效率上也会有所降低。所以需要合理的使用抽象编程。

### 9.尽量避免使用依赖注入框架

使用依赖注入框架貌似看上去把findViewById()这一类的繁琐操作去掉了，但是这些框架为了要搜寻代码中的注解，通常都需要经历较长的初始化过程，并且将一些你用不到的对象也一并加载到内存中。这些用不到的对象会一直站用着内存空间，可能很久之后才会得到释放，所以可能多敲几行代码是更好的选择。

### 10.使用多个进程

谨慎使用，多数应用程序不该在多个进程中运行的，一旦使用不当，它甚至会增加额外的内存而不是帮我们节省内存。这个技巧比较适用于哪些需要在后台去完成一项独立的任务，和前台是完全可以区分开的场景。比如音乐播放，关闭软件，已经完全由Service来控制音乐播放了，系统仍然会将许多UI方面的内存进行保留。在这种场景下就非常适合使用两个进程，一个用于UI展示，另一个用于在后台持续的播放音乐。关于实现多进程，只需要在Manifast文件的应用程序组件声明一个android:process属性就可以了。进程名可以自定义，但是之前要加个冒号，表示该进程是一个当前应用程序的私有进程。

### 11.分析内存的使用情况

系统不可能将所有的内存都分配给我们的应用程序，每个程序都会有可使用的内存上限，被称为堆大小。不同的手机堆大小不同，如下代码可以获得堆大小：
ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
int heapSize = manager.getMemoryClass();
结果以MB为单位进行返回，我们开发时应用程序的内存不能超过这个限制，否则会出现OOM。

### 12.Android的GC操作

Android系统会在适当的时机触发GC操作，一旦进行GC操作，就会将一些不再使用的对象进行回收。GC操作会从一个叫做Roots的对象开始检查，所有它可以访问到的对象就说明还在使用当中，应该进行保留，而其他的对系那个就表示已经不再被使用了。

### 13.Android中内存泄漏

Android中的垃圾回收机制并不能防止内存泄漏的出现导致内存泄漏最主要的原因就是某些长存对象持有了一些其它应该被回收的对象的引用，导致垃圾回收器无法去回收掉这些对象，也就是出现内存泄漏了。比如说像Activity这样的系统组件，它又会包含很多的控件甚至是图片，如果它无法被垃圾回收器回收掉的话，那就算是比较严重的内存泄漏情况了。 举个例子，在MainActivity中定义一个内部类，实例化内部类对象，在内部类新建一个线程执行死循环，会导致内部类资源无法释放，MainActivity的控件和资源无法释放，导致OOM，可借助一系列工具，比如LeakCanary。

### 15.避免创建不必要的对象

不必要的对象我们应该避免创建：
如果有需要拼接的字符串，那么可以优先考虑使用StringBuffer或者StringBuilder来进行拼接，而不是加号连接符，因为使用加号连接符会创建多余的对象，拼接的字符串越长，加号连接符的性能越低。
在没有特殊原因的情况下，尽量使用基本数据类型来代替封装数据类型，int比Integer要更加有效，其它数据类型也是一样。
当一个方法的返回值是String的时候，通常需要去判断一下这个String的作用是什么，如果明确知道调用方会将返回的String再进行拼接操作的话，可以考虑返回一个StringBuffer对象来代替，因为这样可以将一个对象的引用进行返回，而返回String的话就是创建了一个短生命周期的临时对象。
基本数据类型的数组也要优于对象数据类型的数组。另外两个平行的数组要比一个封装好的对象数组更加高效，举个例子，Foo[]和Bar[]这样的数组，使用起来要比Custom(Foo,Bar)[]这样的一个数组高效的多。
尽可能地少创建临时对象，越少的对象意味着越少的GC操作。

### 16.静态优于抽象

如果你并不需要访问一个对系那个中的某些字段，只是想调用它的某些方法来去完成一项通用的功能，那么可以将这个方法设置成静态方法，调用速度提升15%-20%，同时也不用为了调用这个方法去专门创建对象了，也不用担心调用这个方法后是否会改变对象的状态(静态方法无法访问非静态字段)。

### 19.使用增强型for循环语法

static class Counter {  
    int mCount;  
}  
Counter[] mArray = ...  
public void zero() {  
    int sum = 0;  
    for (int i = 0; i < mArray.length; ++i) {  
        sum += mArray[i].mCount;  
    }  
}  
public void one() {  
    int sum = 0;  
    Counter[] localArray = mArray;  
    int len = localArray.length;  
    for (int i = 0; i < len; ++i) {  
        sum += localArray[i].mCount;  
    }  
}  
public void two() {  
    int sum = 0;  
    for (Counter a : mArray) {  
        sum += a.mCount;  
    }  
}  
zero()最慢，每次都要计算mArray的长度，one()相对快得多，two()方法在没有JIT(Just In Time Compiler)的设备上是运行最快的，而在有JIT的设备上运行效率和one()方法不相上下，需要注意这种写法需要JDK1.5之后才支持。
注意ArrayList手写的循环比增强型for循环更快，其他的集合没有这种情况。因此默认情况下使用增强型for循环，而遍历ArrayList使用传统的循环方式。

### 20.多使用系统封装好的API

系统提供不了的Api完成不了我们需要的功能才应该自己去写，因为使用系统的Api很多时候比我们自己写的代码要快得多，它们的很多功能都是通过底层的汇编模式执行的。 举个例子，实现数组拷贝的功能，使用循环的方式来对数组中的每一个元素一一进行赋值当然可行，但是直接使用系统中提供的System.arraycopy()方法会让执行效率快9倍以上。

### 21.避免在内部调用Getters/Setters方法

面向对象中封装的思想是不要把类内部的字段暴露给外部，而是提供特定的方法来允许外部操作相应类的内部字段。但在Android中，字段搜寻比方法调用效率高得多，我们直接访问某个字段可能要比通过getters方法来去访问这个字段快3到7倍。但是编写代码还是要按照面向对象思维的，我们应该在能优化的地方进行优化，比如避免在内部调用getters/setters方法。

### 23.Java代码的优化原则

(1)使用静态工厂方法
使用静态工厂方法代替构造方法，的优点是：工厂方法可以有具体的名称，让创建的实例名称更有意义；静态工厂方法不一定返回新的对象，也可以返回已经存在的对象，比如单例；静态工厂方法可以返回当前类的子类的对象. 
(2)避免重复创建对象
如
public class Person{
    public boolean isTeacher(){
        Company company = new Company();
        return company.getType.equals(“school”);
    }
}
可以修改为
public class Person{
    private static Company company;
    static{
        company = new Company();
    }
    public boolean isTeacher(){
        Company company = new Company();
        return company.getType.equals(“school”);
    }
}
这样每次在使用该类的时候都会先执行static代码块内部的语句。可以将构造方法设置为private，使用单例模式来节省内存开支。
(3)返回长度为零的集合而不是null
这样的好处是无需再判断集合是否为null了，只要使用一个foreach循环，为空时不进入循环体内即可。所以相比于返回null的形式，可以减少一个判断的过程。
(4)通过接口引用对象
如果类实现了接口，在定义类变量的时候应尽量使用接口，而不是直接使用类。比如ArrayList实现了List接口，那么在定义ArrayList时尽量使用下面的形式：
List<String> list = new ArrayList<>();
它的好处是定义变量会更加灵活，可以改变前面的类的形式，比如改用LinkedList亦可。

### 25.常用的程序性能测试方法

1. 时间测试：方式很简单只要在代码的上面和下面定义一个long型的变量，并赋值给当前的毫秒数即可。比如

        long sMillis = System.currentTimeMillis();
        // ...代码块
        long eMillis = System.currentTimeMillis();

然后两者相减即可得到程序的运行时间。

2. 内存消耗测试：获取代码块前后的内存，然后相减即可得到这段代码当中的内存消耗。获取当前内存的方式是

        long total = Runtime.getRuntime().totalMemory(); // 获取系统中内存总数
        long free = Runtime.getRuntime().freeMemory(); // 获取剩余的内存总数
        long used = total - free; // 使用的内存数

在使用的时候只要在代码块的两端调用Runtime.getRuntime().freeMemory()然后再相减即可得到使用的内存总数。


### 合理设置buffer

在读一个文件我们一般会设置一个buffer。即先把文件读到buffer中，然后再读取buffer的数据。所以: 真正对文件的次数 = 文件大小 / buffer大小 。 所以如果你的buffer比较小的话，那么读取文件的次数会非常多。当然在写文件时buffer是一样道理的。

很多同学会喜欢设置1KB的buffer，比如byte buffer[] = new byte[1024]。如果要读取的文件有20KB， 那么根据这个buffer的大小，这个文件要被读取20次才能读完。

最佳实践 -> buffer应该设置多大呢？

java默认的buffer为8KB，最少应该为4KB。那么如何更智能的确定buffer大小呢？

buffer的大小不能大于文件的大小。
buffer的大小可以根据文件保存所�挂载的目录的block size, 什么意思呢？ 来看一下SQLiteGlobal.java是如何确定buffer大小的 :
public static int getDefaultPageSize() { 
    return SystemProperties.getInt("debug.sqlite.pagesize", new StatFs("/data").getBlockSize());
}

### Bitmap 的解码

在Android4.4以上的系统上，对于Bitmap的解码，decodeStream()的效率要高于decodeFile()和decodeResource(), 而且高的不是一点。所以解码Bitmap要使用decodeStream()，同时传给decodeStream()的文件流是BufferedInputStream

val bis =  BufferedInputStream(FileInputStream(filePath))
val bitmap = BitmapFactory.decodeStream(bis,null,ops)





