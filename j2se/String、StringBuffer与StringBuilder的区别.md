## String、StringBuffer与StringBuilder的区别

String类型和StringBuilder及StringBuffer类型的主要性能区别其实在于String是不可变的对象, 因此在每次对String类型进行改变的时候其实都等同于生成了一个新的String对象，然后将指针指向新的String对象，所以经常改变内容的字符串最好不要用String，因为每次生成对象都会对系统性能产生影响，特别当内存中无引用对象多了以后，JVM的GC就会开始工作，那速度是一定会相当慢的。

StringBuilder和StringBuffer与String不同，它内部维护了一个字符串数组`char[] value;`。在每次调用append()方法的时候会进行如下处理：

    public AbstractStringBuilder append(char c) {
        ensureCapacityInternal(count + 1);
        value[count++] = c;
        return this;
    }
这里的ensureCapacityInternal()方法是先保证数组的长度满足要求，如果不足以满足要求就对数组进行扩容。然后，将指定的值添加到数组中。最后，当全部的值，赋值完毕之后，可以调用toString()方法:

    public String toString() {
        return new String(value, 0, count);
    }
将拼接好的字符串返回过去。我们可以看到实际上是在该方法内部调用了String的构造方法构建String之后返回。

StringBuilder和StringBuffer不同之处就在于StringBuffer的全部方法都使用了sychronized进行修饰。