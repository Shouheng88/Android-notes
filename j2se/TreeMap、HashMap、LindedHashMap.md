## TreeMap、HashMap、LindedHashMap

通常在开发的过程中使用HashMap比较多，在Map中在Map中插入、删除和定位元素，HashMap是最好的选择。但如果您要按自然顺序或自定义顺序遍历键，那么TreeMap会更好。如果需要输出的顺序和输入的相同,那么用LinkedHashMap可以实现,它还可以按读取顺序来排列。

### 关于HashMap

可以参考：[https://juejin.im/post/5a12d4c95188255ea95b908e](https://juejin.im/post/5a12d4c95188255ea95b908e)

### 关于TreeMap

TreeMap是支持排序的，它内部使用的数据结构是红黑树，所以在这种Map中实现排序就容易得多。下面是TreeMap的put方法，它根据传入的结点的键进行比较来排序。我们只需要关注两个地方就好了：

    public V put(K key, V value) {
        Entry<K,V> t = root;
        if (t == null) {
            compare(key, key); // type (and possibly null) check

            root = new Entry<>(key, value, null);
            size = 1;
            modCount++;
            return null;
        }
        int cmp;
        Entry<K,V> parent;
        // 在这里判断有没有为该Map设置比较器，如果设置了比较器，就使用比较器进行比较
        Comparator<? super K> cpr = comparator;
        if (cpr != null) {
            do {
                parent = t;
                // 这里和下面的一样，其实就是使用比较器比较的结果决定将结点插入到左边还是右边
                cmp = cpr.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                    return t.setValue(value);
            } while (t != null);
        } else {
            if (key == null)
                throw new NullPointerException();
            @SuppressWarnings("unchecked")
                Comparable<? super K> k = (Comparable<? super K>) key;
            do {
                parent = t;
                cmp = k.compareTo(t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                    return t.setValue(value);
            } while (t != null);
        }
        Entry<K,V> e = new Entry<>(key, value, parent);
        if (cmp < 0)
            parent.left = e;
        else
            parent.right = e;
        fixAfterInsertion(e);
        size++;
        modCount++;
        return null;
    }

### 关于LinkedHashMap

下面是LinkedHashMap的定义：

    public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}

所以，它继承了HashMap，其内部的许多方法都是直接使用了HashMap中的方法。因为我们知道每次当我们使用HashMap.put()方法向HashMap中插入值的时候，都会对要插入的键值对使用

    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        LinkedHashMap.Entry<K,V> p =
            new LinkedHashMap.Entry<K,V>(hash, key, value, e);
        linkNodeLast(p);
        return p;
    }


方法来生成一个新的结点。所以在LinkedHashMap中就在这个方法上面做文章。在LinkedHashMap中定义了如下两个变量：

    transient LinkedHashMap.Entry<K,V> head;
    transient LinkedHashMap.Entry<K,V> tail;

也就是说LinkedHashMap实际用的是双向链表的结构。在newNode()方法中调用了linkNodeLast()方法：

    private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
        LinkedHashMap.Entry<K,V> last = tail;
        tail = p;
        if (last == null)
            head = p;
        else {
            p.before = last;
            last.after = p;
        }
    }

从上面可以看出，它是将指定的结点添加到上面的链表中，这样，整个数据结构既维护了一份数据在HashMap中，又维护了一份在LinkedHashMap中。所以，这个类具有HashMap所不具有的属性——双向链表。如果需要输出的顺序和输入的相同,那么用LinkedHashMap可以实现. 比如应用场景：购物车等需要顺序的。
