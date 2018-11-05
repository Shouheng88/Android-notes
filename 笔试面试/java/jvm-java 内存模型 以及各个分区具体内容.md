# jvm-java 内存模型 以及各个分区具体内容

1. 每一个应用程序都有一个JVM，而不是多个应用程序共享一个JVM；
2. 首先通过编译器，把java源文件编译成 jvm语法的字节码文件。这个过程，是不涉及到JVM的；然后，jvm通过类加载，把需要的类字节码文件，加载进内存中。
3. jvm运行时内存分为两部分：线程共享内存和线程私有内存
	1. 线程共享内存包括：堆、方法区（包含运行时常量池）；
	2. 线程非共享内存包括：虚拟机栈，本地方法栈，PC程序寄存器； 
	3. 栈里面存储着一个个栈帧，每一个栈帧可看做一个方法的调用，包含方法信息；
	4. 方法区不是执行方法的，执行方法的内存在java栈中；
	5. 方法区是涉及到类加载的时候，加载进来的类的信息、常量、字段、方法代码等的信息；
	6. 一旦线程结束，线程私有内存也将释放，所以这部分内存不需要关心回收。


参考：

1. [http://blog.csdn.net/steady_pace/article/details/51254740](http://blog.csdn.net/steady_pace/article/details/51254740)
2. [https://github.com/Shouheng88/Java-Programming/blob/master/JVM/2.Java%E5%86%85%E5%AD%98%E5%8C%BA%E5%9F%9F%E5%92%8C%E5%86%85%E5%AD%98%E6%BA%A2%E5%87%BA%E5%BC%82%E5%B8%B8.md](https://github.com/Shouheng88/Java-Programming/blob/master/JVM/2.Java%E5%86%85%E5%AD%98%E5%8C%BA%E5%9F%9F%E5%92%8C%E5%86%85%E5%AD%98%E6%BA%A2%E5%87%BA%E5%BC%82%E5%B8%B8.md)