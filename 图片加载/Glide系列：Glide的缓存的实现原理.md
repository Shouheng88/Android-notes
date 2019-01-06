# Glide 系列-3：Glide 缓存的实现原理（4.8.0）

## 1、在 Glide 中配置缓存的方式

首先，我们可以在自定义的 GlideModule 中制定详细的缓存策略。即在 `applyOptions()` 中通过直接调用 `GlideBuilder` 的方法来指定缓存的信息：

```java
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_DIR, DISK_CACHE_SIZE));
        builder.setMemoryCache(...);
        builder.setDiskCache(...);
        // ... 略
    }
```

另外，我们在每个图片加载请求中自定义当前图片加载请求的缓存策略，

```java
    Glide.with(getContext())
        .load("https://3-im.guokr.com/0lSlGxgGIQkSQVA_Ja0U3Gxo0tPNIxuBCIXElrbkhpEXBAAAagMAAFBO.png")
        .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.AUTOMATIC))
        .apply(RequestOptions.skipMemoryCacheOf(false))
        .into(getBinding().iv);
```

以上是两个比较常用的缓存的配置方式，具体的 API 可以查看相关的源码了解

## 2、Glide 缓存的源码分析

首先, 我们在 `applyOptions()` 方法中的配置会在实例化单例的 Glide 对象的时候被调用. 所以, 这些方法的作用范围是全局的, 对应于整个 Glide.  下面的方法是 `RequestBuilder` 的 `build()` 方法, 也就是我们最终完成构建 Glide 的地方. 我们可以在这个方法中了解 `RequestBuilder` 为我们提供了哪些与缓存相关的方法. 以及默认的缓存配置.

```java
  Glide build(@NonNull Context context) {
    // ... 无关代码, 略

    if (diskCacheExecutor == null) {
      diskCacheExecutor = GlideExecutor.newDiskCacheExecutor();
    }

    if (memorySizeCalculator == null) {
      memorySizeCalculator = new MemorySizeCalculator.Builder(context).build();
    }

    if (bitmapPool == null) {
      int size = memorySizeCalculator.getBitmapPoolSize();
      if (size > 0) {
        bitmapPool = new LruBitmapPool(size);
      } else {
        bitmapPool = new BitmapPoolAdapter();
      }
    }

    if (arrayPool == null) {
      arrayPool = new LruArrayPool(memorySizeCalculator.getArrayPoolSizeInBytes());
    }

    if (memoryCache == null) { // 默认的缓存配置
      memoryCache = new LruResourceCache(memorySizeCalculator.getMemoryCacheSize());
    }

    if (diskCacheFactory == null) {
      diskCacheFactory = new InternalCacheDiskCacheFactory(context);
    }

    if (engine == null) {
      engine = new Engine(/*各种参数*/);
    }

    return new Glide(/*各种方法*/);
  }
```

这里我们对 `MemorySizeCalculator` 这个参数进行一些说明. 顾名思义, 它是缓存大小的计算器, 即用来根据当前设备的环境计算可用的缓存空间 (主要针对的时基于内存的缓存).

```java
  MemorySizeCalculator(MemorySizeCalculator.Builder builder) {
    this.context = builder.context;

    arrayPoolSize =
        isLowMemoryDevice(builder.activityManager)
            ? builder.arrayPoolSizeBytes / LOW_MEMORY_BYTE_ARRAY_POOL_DIVISOR
            : builder.arrayPoolSizeBytes;
    // 计算APP可申请最大使用内存，再乘以乘数因子，内存过低时乘以0.33，一般情况乘以0.4
    int maxSize =
        getMaxSize(
            builder.activityManager, builder.maxSizeMultiplier, builder.lowMemoryMaxSizeMultiplier);

    // ARGB_8888 ,每个像素占用4个字节内存
    // 计算屏幕这么大尺寸的图片占用内存大小
    int screenSize = widthPixels * heightPixels * BYTES_PER_ARGB_8888_PIXEL;
    // 计算目标位图池内存大小
    int targetBitmapPoolSize = Math.round(screenSize * builder.bitmapPoolScreens);
    // 计算目标Lrucache内存大小，也就是屏幕尺寸图片大小乘以2
    int targetMemoryCacheSize = Math.round(screenSize * builder.memoryCacheScreens);
    // 最终APP可用内存大小
    int availableSize = maxSize - arrayPoolSize;
    if (targetMemoryCacheSize + targetBitmapPoolSize <= availableSize) {
      // 如果目标位图内存大小+目标Lurcache内存大小小于APP可用内存大小，则OK
      memoryCacheSize = targetMemoryCacheSize;
      bitmapPoolSize = targetBitmapPoolSize;
    } else {
      // 否则用APP可用内存大小等比分别赋值
      float part = availableSize / (builder.bitmapPoolScreens + builder.memoryCacheScreens);
      memoryCacheSize = Math.round(part * builder.memoryCacheScreens);
      bitmapPoolSize = Math.round(part * builder.bitmapPoolScreens);
    }
  }
```

对于, 每个加载请求时对应的 `DiskCacheStrategy` 的设置, 我们之前的文章中已经提到过它的作用位置, 你可以参考之前的文章了解,

[Glide 系列-2：主流程源码分析（4.8.0）](https://juejin.im/post/5c31fbdff265da610e803d4e)

 `DiskCacheStrategy` 的作用位置恰好也是 Glide 的缓存最初发挥作用的地方, 即 Engine 的 `load()` 方法. 这里我们只保留了与缓存相关的逻辑, 从下面的方法中也可以看出, 当根据各个参数构建了用于缓存的键之后先后从两个缓存当中加载数据, 拿到了数据之后就进行回调, 否则就需要从原始的数据源中加载数据. 

```java
  public <R> LoadStatus load(/*各种参数*/) {
    // 根据请求参数得到缓存的键
    EngineKey key = keyFactory.buildKey(model, signature, width, height, transformations,
        resourceClass, transcodeClass, options);

    // 检查内存中弱引用是否有目标图片
    EngineResource<?> active = loadFromActiveResources(key, isMemoryCacheable);
    if (active != null) {
      cb.onResourceReady(active, DataSource.MEMORY_CACHE);
      return null;
    }

    // 检查内存中Lrucache是否有目标图片
    EngineResource<?> cached = loadFromCache(key, isMemoryCacheable);
    if (cached != null) {
      cb.onResourceReady(cached, DataSource.MEMORY_CACHE);
      return null;
    }

    // ...内存中没有图片构建任务往下执行, 略

    return new LoadStatus(cb, engineJob);
  }
```



