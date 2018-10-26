# 比较ArrayList、LinkedList、Vector

它们之间的比较还是比较简单的：

1. **同步性能**：ArrayList和LinkedList是非同步的，而Vector是同步的。也就是Vector在所有的操作方法上面都加了sychronized来进行修饰。这在多线程环境中可以保证被sychronized修饰的方法的安全性，但是在非多线程环境中就会成为一种没有必要的开销（加锁和释放锁是需要消耗一些性能的）。另外，要注意的是加了sychronized充其量只能保证指定的方法本身的原子性，当多个方法组合起来使用的时候，就不一定是原子的了。所以，即使想要在多线程中使用，Vector也不是个好的选择。因此，Vector被抛弃了。
2. **数据结构**:ArrayList和Vector是基于数组的，而LinkedList是基于链表的。所以，它们之间的区别也就是链表数据结构和数组数据结构的区别（比如，链表查找的效率是O(n)，数组的查找效率是O(1)等等）。在ArrayList和Vector的底层实现中，本质上都是使用了System.arraycopy()方法来实现数组的拷贝。
3. **应用场景**：Vector基本不要用。如果应用场景中插入和删除操作比较频繁，就使用LinkedList。如果查询操作比较频繁就使用ArrayList. 另外，可以将LinkedList当成队列来使用。

------

### 更多

关于Java容器更多的内容参考：
[https://github.com/Shouheng88/Java-Programming/blob/master/Java%E8%AF%AD%E8%A8%80/%E5%AE%B9%E5%99%A8.md](https://github.com/Shouheng88/Java-Programming/blob/master/Java%E8%AF%AD%E8%A8%80/%E5%AE%B9%E5%99%A8.md)

或者

[https://juejin.im/post/5a12d4c95188255ea95b908e](https://juejin.im/post/5a12d4c95188255ea95b908e)
