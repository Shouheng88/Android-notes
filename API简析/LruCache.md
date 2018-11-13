# Android 内存缓存框架 LruCache 的源码分析

LruCache 是 Android 提供的一种基于内存的缓存框架。LRU 是 **Least Recently Used** 的缩写，即最近最少使用。当一块内存最近很少使用的时候就会被从缓存中移除。在这篇文章中，我们会先简单介绍 LruCache 的使用，然后我们会对它的源码进行分析。

## 1、基本的使用示例

首先，让我们来简单介绍一下如何使用 LruCache 实现内存缓存。下面是 LruCache 的一个使用示例。

这里我们实现的是对 RecyclerView 的列表的截图的功能。因为我们需要将列表的每个项的 Bitmap 存储下来，然后当所有的列表项的 Bitmap 都拿到的时候，将其按照顺序和位置绘制到一个完整的 Bitmap 上面。如果我们不使用 LruCache 的话，当然也能够是实现这个功能——将所有的列表项的 Bitmap 放置到一个 List 中即可。但是那种方式存在缺点：因为是强引用类型，所以当内存不足的时候会导致 OOM。

在下面的方法中，我们先获取了内存的大小的 8 分之一作为缓存空间的大小，用来初始化 LruCache 对象，然后从 RecyclerView 的适配器中取出所有的 ViewHolder 并获取其对应的 Bitmap，然后按照键值对的方式将其放置到 LruCache 中。当所有的列表项的 Bitmap 都拿到之后，我们再创建最终的 Bitmap 并将之前的 Bitmap 依次绘制到最终的 Bitmap 上面：

    public static Bitmap shotRecyclerView(RecyclerView view) {
        RecyclerView.Adapter adapter = view.getAdapter();
        Bitmap bigBitmap = null;
        if (adapter != null) {
            int size = adapter.getItemCount();
            int height = 0;
            Paint paint = new Paint();
            int iHeight = 0;
            final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

            // 使用内存的 8 分之一作为该缓存框架的缓存空间
            final int cacheSize = maxMemory / 8;
            LruCache<String, Bitmap> bitmaCache = new LruCache<>(cacheSize);
            for (int i = 0; i < size; i++) {
                RecyclerView.ViewHolder holder = adapter.createViewHolder(view, adapter.getItemViewType(i));
                adapter.onBindViewHolder(holder, i);
                holder.itemView.measure(
                        View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                holder.itemView.layout(0, 0, holder.itemView.getMeasuredWidth(),
                        holder.itemView.getMeasuredHeight());
                holder.itemView.setDrawingCacheEnabled(true);
                holder.itemView.buildDrawingCache();
                Bitmap drawingCache = holder.itemView.getDrawingCache();
                if (drawingCache != null) {
                    bitmaCache.put(String.valueOf(i), drawingCache);
                }
                height += holder.itemView.getMeasuredHeight();
            }

            bigBitmap = Bitmap.createBitmap(view.getMeasuredWidth(), height, Bitmap.Config.ARGB_8888);
            Canvas bigCanvas = new Canvas(bigBitmap);
            Drawable lBackground = view.getBackground();
            if (lBackground instanceof ColorDrawable) {
                ColorDrawable lColorDrawable = (ColorDrawable) lBackground;
                int lColor = lColorDrawable.getColor();
                bigCanvas.drawColor(lColor);
            }

            for (int i = 0; i < size; i++) {
                Bitmap bitmap = bitmaCache.get(String.valueOf(i));
                bigCanvas.drawBitmap(bitmap, 0f, iHeight, paint);
                iHeight += bitmap.getHeight();
                bitmap.recycle();
            }
        }

        return bigBitmap;
    }

因此，我们可以总结出 LruCahce 的基本用法如下：

首先，你要声明一个缓存空间的大小，在这里我们用了运行时内存的 8 分之 1 作为缓存空间的大小

    LruCache<String, Bitmap> bitmaCache = new LruCache<>(cacheSize);

**但是应该注意的一个问题是缓存空间的单位的问题**。因为 LruCache 的键值对的值可能是任何类型的，所以你传入的类型的大小如何统计需要自己去指定。后面我们在分析它的源码的时候会指出它的单位的问题。LruCahce 的 API 中也已经提供了计算传入的值的大小的方法。我们只需要在实例化一个 LruCache 的时候覆写该方法即可。而这里我们认为一个 Bitmap 对象所占用的内存的大小不超过 1KB. 

然后，我们可以像普通的 Map 一样调用它的 put() 和 get() 方法向缓存中插入和从缓存中取出数据：
	
    bitmaCache.put(String.valueOf(i), drawingCache);
    Bitmap bitmap = bitmaCache.get(String.valueOf(i));

## 2、LruCahce 源码分析

### 2.1 分析之前：当我们自己实现一个 LruCache 的时候，我们需要考虑什么

在我们对 LruCache 的源码进行分析之前，我们现来考虑一下当我们自己去实现一个 LruCache 的时候需要考虑哪些东西，以此来带着问题阅读源码。

因为我们需要对数据进行存储，并且又能够根据指定的 id 将数据从缓存中取出，所以我们需要使用哈希表表结构。或者使用两个数组，一个作为键一个作为值，然后使用它们的索引来实现映射也行。但是，后者的效率不如前者高。

此外，我们还要对插入的元素进行排序，因为我们需要移除那些使用频率最小的元素。我们可以使用链表来达到这个目的，每当一个数据被用到的时候，我们可以将其移向链表的头节点。这样当要插入的元素大于缓存的最大空间的时候，我们就将链表末位的元素移除，以在缓存中腾出空间。

综合这两点，我们需要一个既有哈希表功能，又有队列功能的数据结构。在 Java 的集合中，已经为我们提供了 LinkedHashMap 用来实现这个功能。

实际上在 Android 中的 LruCache 也正是使用 LinkedHashMap 来实现的。LinkedHashMap 拓展自HashMap。如果理解 HashMap 的话，它的源码就不难阅读。LinkedHashMap 仅在 HashMap 的基础之上，又将各个节点放进了一个双向链表中。每次增加和删除一个元素的时候，被操作的元素会被移到到链表的末尾。Android 中的 LruCahce 就是在 LinkedHashMap 基础之上进行了一层拓展，不过 Android 中的 LruCache 的实现具有一些很巧妙的地方值得我们学习。

### 2.2 LruCache 源代码分析

从上面的分析中我们知道了选择 LinkedHashMap 作为底层数据结构的原因。下面我们分析其中的一些方法。这个类的实现还有许多的细节考虑得非常周到，非常值得我们借鉴和学习。

#### 2.2.1 缓存的最大可用空间

在 LruCache 中有两个字段 size 和 maxSize. maxSize 会在 LruCache 的构造方法中被赋值，用来表示该缓存的最大可用的空间：

    int cacheSize = 4 * 1024 * 1024; // 4MiB，cacheSize 的单位是 KB
    LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    }};
	
这里我们使用 4MB 来设置缓存空间的大小。我们知道 LruCache 的原理是指定了空间的大小之后，如果继续插入元素时，空间超出了指定的大小就会将那些“可以被移除”的元素移除掉，以此来为新的元素腾出空间。那么，因为插入的类型时不确定的，所以具体被插入的对象如何计算大小就应该交给用户来实现。

在上面的代码中，我们直接使用了 Bitmap 的 getByteCount() 方法来获取 Bitmap 的大小。同时，我们也注意到在最初的例子中，我们并没有这样去操作。那样的话一个 Bitmap 将会被当作 1KB 来计算。

这里的 sizeOf() 是一个受保护的方法，显然是希望用户自己去实现计算的逻辑。它的默认值是 1，单位和设置缓存大小指定的 maxSize 的单位相同：

    protected int sizeOf(K key, V value) {
        return 1;
    }

这里我们还需要提及一下：虽然这个方法交给用户来实现，但是在 LruCache 的源码中，不会直接调用这个方法，而是

    private int safeSizeOf(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }
	
所以，这里又增加了一个检查，防止参数错误。其实，这个考虑是非常周到的，试想如果传入了一个非法的参数，导致了意外的错误，那么错误的地方就很难跟踪了。如果我们自己想设计 API 给别人用并且提供给他们自己可以覆写的方法的时候，不妨借鉴一下这个设计。

#### 2.2.2 LruCache 的 get() 方法

下面我们分析它的 get() 方法。它用来从 LruCahce 中根据指定的键来获取对应的值：

    /**
     * 1). 获取指定 key 对应的元素，如果不存在的话就用 craete() 方法创建一个。
     * 2). 当返回一个元素的时候，该元素将被移动到队列的首位；
     * 3). 如果在缓存中不存在又不能创建，就返回n ull
     */
    public final V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V mapValue;
        synchronized (this) {
            // 在这里如果返回不为空的话就会将返回的元素移动到队列头部，这是在 LinkedHashMap 中实现的
            mapValue = map.get(key);
            if (mapValue != null) {
                // 缓存命中
                hitCount++;
                return mapValue;
            }
            // 缓存没有命中，可能是因为这个键值对被移除了
            missCount++;
        }

        // 这里的创建是单线程的，在创建的时候指定的 key 可能已经被其他的键值对占用
        V createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        // 这里设计的目的是防止创建的时候，指定的 key 已经被其他的 value 占用，如果冲突就撤销插入
        synchronized (this) {
            createCount++;
            // 向表中插入一个新的数据的时候会返回该 key 之前对应的值，如果没有的话就返回 null
            mapValue = map.put(key, createdValue);
            if (mapValue != null) {
                // 冲突了，还要撤销之前的插入操作
                map.put(key, mapValue);
            } else {
                size += safeSizeOf(key, createdValue);
            }
        }

        if (mapValue != null) {
            entryRemoved(false, key, createdValue, mapValue);
            return mapValue;
        } else {
            trimToSize(maxSize);
            return createdValue;
        }
    }

这里获取值的时候对当前的实例进行了加锁以保证线程安全。当用 map 的 get() 方法获取不到数据的时候用了 `create()` 方法。因为当指定的键值对找不到的时候，可能它本来就不存在，可能是因为缓存不足被移除了，所以，我们需要提供这个方法让用户来处理这种情况，该方法默认返回 null. 如果用户覆写了 `create()` 方法，并且返回的值不为 null，那么我们需要将该值插入到哈希表中。

插入的逻辑也在同步代码块中进行。这是因为，创建的操作可能过长而且是非同步的。当我们再次向指定的 key 插入值的时候，它可能已经存在值了。所以当调用 map 的 put() 的时候如果返回不为 null，就表明对应的 key 已经有对应的值了，就需要撤销插入操作。最后，当 mapValue 非 null，还要调用 `entryRemoved()` 方法。每当一个键值对从哈希表中被移除的时候，这个方法将会被回调一次。

最后调用了 `trimToSize()` 方法，用来保证新的值被插入之后缓存的空间大小不会超过我们指定的值。当发现已经使用的缓存超出最大的缓存大小的时候，“最近最少使用” 的项目将会被从哈希表中移除。

那么如何来判断哪个是 “最近最少使用” 的项目呢？我们先来看下 `trimToSize()` 的方法定义：

    public void trimToSize(int maxSize) {
        while (true) {
            K key;
            V value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(getClass().getName()
                            + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize) {
                    break;
                }

                // 获取用来移除的 “最近最少使用” 的项目
                Map.Entry<K, V> toEvict = map.eldest();
                if (toEvict == null) {
                    break;
                }

                key = toEvict.getKey();
                value = toEvict.getValue();
                map.remove(key);
                size -= safeSizeOf(key, value);
                evictionCount++;
            }

            entryRemoved(true, key, value, null);
        }
    }

显然，这里是使用了 LinkedHashMap 的 `eldest()` 方法，这个方法的返回值是：

    public Map.Entry<K, V> eldest() {
        return head;
    }
    
也就是 LinkedHashMap 的头结点。那么为什么要移除头结点呢？这不符合 LRU 的原则啊，这里分明是直接移除了头结点。实际上不是这样，魔力发生在 `get()` 方法中。在 LruCache 的 get() 方法中，我们调用了 LinkedHashMap 的 `get()` 方法，这个方法中又会在拿到值的时候调用下面的方法：

    void afterNodeAccess(Node<K,V> e) { // move node to last
        LinkedHashMapEntry<K,V> last;
        if (accessOrder && (last = tail) != e) {
            LinkedHashMapEntry<K,V> p =
                (LinkedHashMapEntry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        }
    }

这里的逻辑是把 `get()` 方法中返回的结点移动到双向链表的末尾。所以，最近最少使用的结点必然就是头结点了。

## 3、总结

以上是我们对 LruCache 的是使用和源码的总结，这里我们实际上只分析了 `get()` 的过程。因为这个方法才是 LruCache 的核心，它包含了插入值和移动最近使用的项目的过程。至于 `put()` 和 `remove()` 两种方法，它们内部实际上直接调用了 LinkedHashMap 的方法。这里我们不再对它们进行分析。


------
**如果您喜欢我的文章，可以在以下平台关注我：**

- 个人主页：[https://shouheng88.github.io/](https://shouheng88.github.io/)
- 掘金：[https://juejin.im/user/585555e11b69e6006c907a2a](https://juejin.im/user/585555e11b69e6006c907a2a)
- Github：[https://github.com/Shouheng88](https://github.com/Shouheng88)
- CSDN：[https://blog.csdn.net/github_35186068](https://blog.csdn.net/github_35186068)
- 微博：[https://weibo.com/u/5401152113](https://weibo.com/u/5401152113)
