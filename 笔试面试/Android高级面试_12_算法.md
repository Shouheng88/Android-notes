# Android 高级面试 12 —— 算法

## 1.冒泡排序算法

```java
    protected static void exchange(Comparable[] arr, int fromPos, int toPos) {
        Comparable temp = arr[fromPos];
        arr[fromPos] = arr[toPos];
        arr[toPos] = temp;
    }

    protected static boolean less(Comparable cmp1, Comparable cmp2) {
        return cmp1.compareTo(cmp2) < 0;
    }
```

 ```java
    public static void sort(Comparable[] arr) { // 增序
        boolean exchanged = true; // 默认值为 true
        for (int i=0; i<arr.length-1 && exchanged; i++) { // 将布尔值加入到 for 条件中
            exchanged = false; // 开始循环的时候默认是 false
            for (int j=arr.length-1; j>i; j--) {
                if (less(arr[j], arr[j-1])) {
                    exchange(arr, j, j-1);
                    exchanged = true; // 当进行了交换的时候才设置为 true
                }
            }
        }
    }
```

## 2.快速排序

 ```java
	public class Quick extends Sort{
	
	    public static void sort(Comparable[] arr) {
	        sort(arr, 0, arr.length - 1);
	    }
	
	    private static void sort(Comparable[] arr, int lo, int hi) {
	        if (hi <= lo) {
	            return;
	        }
	        int j = partition(arr, lo, hi);
	        sort(arr, lo, j - 1);
	        sort(arr, j + 1, hi);
	    }
	
	    private static int partition(Comparable[] arr, int lo, int hi) {
	        int i = lo, j = hi + 1;
	        Comparable v = arr[lo];
	        while (true) {
	            while (less(arr[++i], v)) { // 找到一个大于v的元素
	                if (i == hi) {
	                    break;
	                }
	            }
	            while (less(v, arr[--j])) { // 找到一个小于v的元素
	                if (j == lo) {
	                    break;
	                }
	            }
	            if (i >= j) { // 两个指针相遇了，退出循环
	                break;
	            }
	            exchange(arr, i, j); // 交换i和j处的元素的位置，这样可以保证v左小于v，v右都大于v
	        }
	        exchange(arr, lo, j); // 还要注意最终要将v和a[j]交换
	        return j;
	    }
	}
```

## 3.链表翻转

```java
    private static Node reverse(Node head) {
        if (head == null || head.next == null) return head;

        Node current = head;
        Node next = null; //定义当前结点的下一个结点
        Node reverseHead = null;  //反转后新链表的表头
        while (current != null) {
            next = current.next;  //暂时保存住当前结点的下一个结点，因为下一次要用
            current.next = reverseHead; //将current的下一个结点指向新链表的头结点
            reverseHead = current;
            current = next;   // 操作结束后，current节点后移
        }

        return reverseHead;
    }
```
