## Map、Set、List、Queue、Stack的特点与用法

Map与其他的几个不同。Map是基于键值对的，键Key是唯一不能重复的，一个键对应一个值，值可以重复。对于Java中的Map，它有几个实现，如HashMap和TreeMap等。可以根据Key到指定的Map中找指定的Value. 

剩下的几个，都是普通的列表。Set列表中的元素是不允许重复的，在Java中的Set都是借助于Map来实现的，即将要添加到Set中的元素放在Map的Key上面，而Value设置为一个默认的对象。

List是一个列表，它的实现方式通常有基于数组的，如ArrayList；和基于链表的，如LinkedList. 

Queue是基于队列的概念，而Stack是基于栈的概念。

