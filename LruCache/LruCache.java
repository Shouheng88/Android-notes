package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    
    // 内部使用LinkedHashMap来实现
    private final LinkedHashMap<K, V> map;

    // 这里的两个字段是缓存的大小和最大的大小，默认每添加一个元素则size加1
    // 实际上在该类中提供了sizeOf()方法给用户使用，目的就在于让用户自己去定义一个元素的大小
    private int size;
    private int maxSize;

    private int putCount;
    private int createCount;
    private int evictionCount;
    private int hitCount;
    private int missCount;

    // 传入最大的空间为参数创建实例
    public LruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<K, V>(0, 0.75f, true);
    }

    public void resize(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }

        synchronized (this) {
            this.maxSize = maxSize;
        }
        trimToSize(maxSize);
    }
    
    // 获取指定key对应的元素，如果不存在的话就用craete()方法创建一个。
    // 当返回一个元素的时候，该元素将被移动到队列的首位。
    // 如果在缓存中不存在又不能创建，就返回null
    public final V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V mapValue;
        synchronized (this) {
            // 在这里如果返回不为空的话就会将返回的元素移动到队列头部，这是在LinkedHashMap中实现的
            mapValue = map.get(key);
            if (mapValue != null) {
                hitCount++;
                return mapValue;
            }
            missCount++;
        }

        
        // 这里的创建是单线程的，在创建的时候指定的key可能已经被其他的键值对占用
        V createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        // 这里设计的目的是防止创建的时候，指定的key已经被其他的value占用，如果冲突就撤销插入
        synchronized (this) {
            createCount++;
            // 向表中插入一个新的数据的时候会返回该key之前对应的值，如果没有的话就返回null
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

    public final V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        V previous;
        synchronized (this) {
            putCount++;
            size += safeSizeOf(key, value);
            previous = map.put(key, value);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, value);
        }

        trimToSize(maxSize);
        return previous;
    }

    // 根据传入的大小调整队列中的元素，如果需要的话会移除队列尾部的元素
    // 这个方法会在每次插入和移除元素的时候调用
    public void trimToSize(int maxSize) {
        while (true) {
            K key;
            V value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize) {
                    break;
                }

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

    public final V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V previous;
        synchronized (this) {
            previous = map.remove(key);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, null);
        }

        return previous;
    }

    // 下面两个方法相当于给用户提供了可以自己实现的一些逻辑的“接口”，因为被移除或者找不到可能是因为缓存空间的问题被从队列中移除，所以
    // 应该在移除或者找不到的时候给用户一个自己实现一些逻辑的机会
    
    // 当其元素被移除的时候会被回调的方法，单线程的。这个方法默认是空的，是想交给用户来实现，相当于给用户提供了一个可以自己实现的“接口”
    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {}

    // 这个方法跟上面的方法一样，单线程，采用了默认的实现，也是给用户提供的一个接口，只在get方法中被调用
    protected V create(K key) {
        return null;
    }

    // 在sizeOf()方法基础上增加了一层检查包含，这个设计考虑很周到，因为sizeOf()交给用户来实现的
    private int safeSizeOf(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }

    // 返回元素的所占的空间大小，可以交给用户自己来实现
    protected int sizeOf(K key, V value) {
        return 1;
    }

    // 清除缓存
    public final void evictAll() {
        trimToSize(-1); // -1 will evict 0-sized elements
    }
}
