
## 异常处理

对于未捕获的异常，借助 Thread 的静态方法来进行处理

```java
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
         defaultUncaughtExceptionHandler = eh;
     }
```

## multidex

这是因为安装应用时，有一步是使用 DexOpt 对 Dex 进行优化。这个过程会生成一个 ODex 文件，执行 ODex 的效率会比直接执行 Dex 文件的效率要高很多。在早期的 Android 系统中，DexOpt 把每一个类的方法 id 检索起来，存在一个链表结构里面。但是这个链表的长度是用一个 short 类型来保存的，导致了方法 id 的数目不能够超过65536 个。尽管在新版本的 Android 系统中，修复了 DexOpt 的这个问题，但是我们仍然需要对低版本的 Android 系统做兼容。

为了解决方法数超限的问题，需要启用 multidex 将该 dex文 件拆成多个。

## 动态布局

就是指服务端使用 API 下发数据信息，然后客户端根据下发的信息进行动态布局。

另一层含义可能是在代码中进行动态布局而不是使用 XML 的方式。




