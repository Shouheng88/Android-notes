对这个问题，如果要回答的全面的话，我们应该这么讲：

1. 当参与==比较的两个元素中有一个是值类型的，那么就按照值类型来比较。而引用类型按照值来比较的时候使用的是它们的hashCode的返回结果。只有当参与比较的两个元素都是引用类型的，那么才按照引用类型来比较，即比较它们的hashCode的返回结果。
2. 当使用equals方法进行比较的时候，实际的比较结果取决于equals方法的具体实现，在Object的默认实现中，是使用==来实现的。也就是说使用了按引用来比较的方式。不过，比如Integer和String等，它们在自己的类中又实现了该方法，而它们实现该方法的时候是按照值来进行比较的。
3. 另外就是关于覆写equals和hashCode方法的问题，覆写它们要遵循一定的原则，不过这些工作完全可以由IDEA代劳。

    public static void main(String...args) {
        Integer integer = new Integer(2);
        int hash = integer.hashCode();

        // case1:结果为true，说明当参与==比较的两个中有一个是基本类型，就按照基本类型来比较
        System.out.println(hash == integer);

        Integer integer1 = new Integer(2);
        // case2:结果为false，说明是按照两个对象的引用来比较的
        System.out.println(integer == integer1);
        // Case3:结果为true，说明是使用equals方法进行比较的，而equals是用了它们的值来比较
        System.out.println(integer.equals(integer1));

        MyObj myObj = new MyObj(2);
        MyObj myObj1 = new MyObj(2);
        // case4:比较结果为false，说明是按照两个对象的引用来比较的
        System.out.println(myObj == myObj1);
        // case5:比较结果为false，因为用了Object的equals方法实现，而该方法实现中使用==比较的
        System.out.println(myObj.equals(myObj1));
    }

    private static class MyObj {
        private int o;

        public MyObj(int o) {
            this.o = o;
        }
    }

输出结果：

	true
	false
	true
	false
	false