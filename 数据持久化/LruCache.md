# LruCache

## 基本的使用示例

下面是LruCache的一个用法，

    public static Bitmap shotRecyclerView(RecyclerView view) {
        RecyclerView.Adapter adapter = view.getAdapter();
        Bitmap bigBitmap = null;
        if (adapter != null) {
            int size = adapter.getItemCount();
            int height = 0;
            Paint paint = new Paint();
            int iHeight = 0;
            final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

            // Use 1/8th of the available memory for this memory cache.
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

在上面的代码中，我们可以总结出LruCahce的基本用法如下：

首先，你要声明一个缓存空间的大小，在这里我们用了运行时内存的8分之1作为缓存空间的大小

    LruCache<String, Bitmap> bitmaCache = new LruCache<>(cacheSize);

然后，我们可以像普通的Map一样调用它的put()和get()方法向缓存中插入和从缓存中取出数据。
	
    bitmaCache.put(String.valueOf(i), drawingCache);
    Bitmap bitmap = bitmaCache.get(String.valueOf(i));

## 当我们自己实现一个LruCache的时候，我们需要考虑什么

### 数据结构

因为我们需要对数据进行存储，并且又能够根据指定的id将数据从缓存中取出，所以我们需要使用Hash表。

此外，我们还要对插入的元素进行排序，因为我们需要移除那些使用频率最小的元素。我们可以使用链表来达到这个目的，每当一个数据被用到的时候，我们可以将其移向链表的头节点。这样当要插入的元素大于缓存的最大空间的时候，我们就将链表末位的元素移除，以在缓存中腾出空间。

综合这两点，我们需要一个既有Hash功能，又有队列功能的数据结构。在Java的集合中，已经为我们提供了LinkedHashMap。

实际上在Android中的LruCache也正是使用了LinkedHashMap来实现的，它拓展自HashMap。如果理解HashMap的话，它的源码就不难阅读。仅仅是在HashMap的基础之上，又将各个节点放进了一个双向链表中。每次增加和删除一个元素的时候，被操作的元素会被移到到链表的末尾。Android中的LruCahce就是在这个基础之上进行了一层拓展。

## Lru源代码分析

从上面的分析中我们知道了选择LinkedHashMap作为底层数据结构的原因，下面我们分析其中的一些方法。这个类的实现还有许多的细节考虑得非常周到，非常值得我们借鉴和学习。

在LruCache中有两个字段size和maxSize，这里我们来说明一下它们的作用：

    int cacheSize = 4 * 1024 * 1024; // 4MiB
    LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    }};
	
这里我们使用4MB来设置缓存空间的大小。我们知道这个缓存的原理是指定了空间的大小之后，如果继续插入元素时，空间超出了指定的大小就会将那些“可以被移除”的元素移除掉，以为新的元素腾出空间。那么，因为插入的类型时不确定的，所以具体被插入的对象如何计算大小就应该交给用户来实现。于是，LruCahce就为我们提供了一个受保护的方法：

    protected int sizeOf(K key, V value) {
        return 1;
    }
	
这个方法就是提供给用户来计算自己指定的Value的大小的方法。默认值是1，实际的意义取决于maxSize。（所以，我们最开始的示例，每插入一个Bitmap就会被看作占用了1KB的空间，可以自己考虑一下。）

而且，这里稍微提及一下，虽然这个方法交给用户来实现，但是在LruCache的源码中，每次都不会调这个方法，而是

    private int safeSizeOf(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }
	
为什么呢？因为不放心呗。所以，这里又增加了一个检查，防止参数错误。其实，这个考虑是非常周到的，试想如果传入了一个非法的参数，导致了意外的错误，那么错误的地方就很难跟踪了。如果我们自己想设计API给别人用并且提供给他们自己可以覆写的方法的时候，不妨借鉴一下这个设计。

下面我们分析它的get方法。其实put和remove方法和这个方法差不多，我们只看这一个方法就好了：

    public final V get(K key) {
        if (key == null) throw new NullPointerException("key == null");

        V mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                hitCount++;
                return mapValue;
            }
            missCount++;
        }

        V createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        synchronized (this) {
            createCount++;
            mapValue = map.put(key, createdValue);

            if (mapValue != null) {
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

这里实际获取值的时候对当前的实例进行了加锁，这保证了线程的安全。当用map的get方法获取不到数据的时候用了create()方法。提供这个方法的意义是，因为当缓存不够的时候会有元素被移除，那些没要找不到的元素可能确实曾经被用户插入到哈希表中，所以当找不到的时候，我们给用户提供一个方法，让他们可以处理这种情形。

然后如果create()方法获取到了非null的值，就将值插入到map中。插入的逻辑也在同步代码块中进行。因为，创建的操作可能过长，而且创建是非同步的。当我们再次插入的时候，指定的key可能已经有值了。所以当调用map的put的时候如果返回不为null，就表明指定的key已经有对应的value了，就需要撤销插入操作。最后，当mapValue非null，还要调用entryRemoved方法。这其实也是把撤销操作的情形的处理交给用户细化实现。

最后调用了trimToSize()方法，这个方法每当插入和移除元素的时候都会被调用，目的是对队列进行调整，以腾出更多的空间来（即满足size<maxSize）。
	
我们还要注意下，这里面对两个部分进行加锁，这考虑相当周到的。调用create()方法的时候可能耗时太长，不加锁，就可以提升多线程环境中的并发效果。

以上就是对LruCache的分析，源码的注释在[这里](LruCache.java)	