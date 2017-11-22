## throw和throws有什么区别

下面是throw和throws的主要用法：

    public void sort() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

我们可以总结一下它们的区别：

1. throw代表动作，表示抛出一个异常的动作；throws代表一种状态，代表方法可能有异常抛出
2. throw用在方法实现中，而throws用在方法声明中
3. throw只能用于抛出一种异常，而throws可以抛出多个异常