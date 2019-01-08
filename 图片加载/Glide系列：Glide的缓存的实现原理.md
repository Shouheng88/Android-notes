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

以上是两个比较常用的缓存的配置方式，具体的 API 可以查看相关的源码了解. 

不论 Glide 还是其他的框架的缓存无非就是基于内存的缓存和基于磁盘的缓存两种，而且缓存的管理算法基本都是 LRU. 针对内存缓存，Android 中提供了 `LruCache`，笔者在之前的文章中曾经分析过这个框架：

[《Android 内存缓存框架 LruCache 的源码分析》](https://juejin.im/post/5bea581be51d451402494af2)

至于磁盘缓存， Glide 和 OkHttp 都是基于 [DiskLruCache](https://github.com/JakeWharton/DiskLruCache) 进行了封装。这个框架本身的逻辑并不复杂，只是指定了一系列缓存文件的规则，读者可以自行查看源码学习。本文中涉及上述两种框架的地方不再详细追究缓存框架的源码。

## 2、Glide 缓存的源码分析

### 2.1 缓存配置

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

### 2.2 内存缓存

对于, 每个加载请求时对应的 `DiskCacheStrategy` 的设置, 我们之前的文章中已经提到过它的作用位置, 你可以参考之前的文章了解,

[《Glide 系列-2：主流程源码分析（4.8.0）》](https://juejin.im/post/5c31fbdff265da610e803d4e)

 `DiskCacheStrategy` 的作用位置恰好也是 Glide 的缓存最初发挥作用的地方, 即 Engine 的 `load()` 方法. 这里我们只保留了与缓存相关的逻辑, 从下面的方法中也可以看出, 当根据各个参数构建了用于缓存的键之后先后从两个缓存当中加载数据, 拿到了数据之后就进行回调, 否则就需要从原始的数据源中加载数据. 

```java
  public <R> LoadStatus load(/*各种参数*/) {
    // 根据请求参数得到缓存的键
    EngineKey key = keyFactory.buildKey(model, signature, width, height, transformations,
        resourceClass, transcodeClass, options);

    // 检查内存中弱引用是否有目标图片
    EngineResource<?> active = loadFromActiveResources(key, isMemoryCacheable); // 1
    if (active != null) {
      cb.onResourceReady(active, DataSource.MEMORY_CACHE);
      return null;
    }

    // 检查内存中Lrucache是否有目标图片
    EngineResource<?> cached = loadFromCache(key, isMemoryCacheable); // 2
    if (cached != null) {
      cb.onResourceReady(cached, DataSource.MEMORY_CACHE);
      return null;
    }

    // ...内存中没有图片构建任务往下执行, 略

    return new LoadStatus(cb, engineJob);
  }
```

这里存在两个方法，即 1 处的从弱引用中获取缓存数据，以及 2 处的从内存缓存中获取缓存数据。它们两者之间有什么区别呢？

1. 弱引用的缓存会在内存不够的时候被清理掉，而基于 LruCache 的内存缓存是强引用的，因此不会因为内存的原因被清理掉。LruCache 只有当缓存的数据达到了缓存空间的上限的时候才会将最近最少使用的缓存数据清理出去。
2. 两个缓存的实现机制都是基于哈希表的，只是 LruCahce 除了具有哈希表的数据结构还维护了一个链表。而弱引用类型的缓存的键与 LruCache 一致，但是值是弱引用类型的。
3. 除了内存不够的时候被释放，弱引用类型的缓存还会在 Engine 的资源被释放的时候清理掉。
4. 基于弱引用的缓存是一直存在的，无法被用户禁用，但用户可以关闭基于 LruCache 的缓存。
5. 本质上基于弱引用的缓存与基于 LruCahce 的缓存针对于不同的应用场景，弱引用的缓存算是缓存的一种类型，只是这种缓存受可用内存的影响要大于 LruCache. 

接下来让我们先看下基于弱引用的缓存相关的逻辑，从上面的 1 处的代码开始：

```java
  // Engine#loadFromActiveResources
  private EngineResource<?> loadFromActiveResources(Key key, boolean isMemoryCacheable) {
    if (!isMemoryCacheable) {
      return null;
    }
    EngineResource<?> active = activeResources.get(key); // 1
    if (active != null) {
      active.acquire(); // 2
    }
    return active;
  }

  // ActiveResources#get()
  EngineResource<?> get(Key key) {
    ResourceWeakReference activeRef = activeEngineResources.get(key);
    if (activeRef == null) {
      return null;
    }
    EngineResource<?> active = activeRef.get();
    if (active == null) {
      cleanupActiveReference(activeRef); // 3
    }
    return active;
  }

  // ActiveResources#cleanupActiveReference()
  void cleanupActiveReference(@NonNull ResourceWeakReference ref) {
    activeEngineResources.remove(ref.key);
    if (!ref.isCacheable || ref.resource == null) { // 4
      return;
    }
    EngineResource<?> newResource =
        new EngineResource<>(ref.resource, /*isCacheable=*/ true, /*isRecyclable=*/ false);
    newResource.setResourceListener(ref.key, listener);
    listener.onResourceReleased(ref.key, newResource); // 5
  }

  // Engine#onResourceReleased()
  public void onResourceReleased(Key cacheKey, EngineResource<?> resource) {
    Util.assertMainThread();
    activeResources.deactivate(cacheKey);
    if (resource.isCacheable()) {
      cache.put(cacheKey, resource); // 将数据缓存到 LruCahce
    } else {
      resourceRecycler.recycle(resource);
    }
  }
```

这里的 1 处会先调用 ActiveResources 的 `get()` 从弱引用中拿数据。当拿到了数据之后调用 `acquire()` 方法将 `EngineResource` 的引用计数加 1. 当这个资源被释放的时候，又会将引用计数减 1（参考 EngineResource 的 `release()` 方法）. 
 
当发现了弱引用中引用的 `EngineResource` 不存在的时候会在 3 处执行一次清理的逻辑。并在 5 处调用回调接口将弱引用中缓存的数据缓存到 LruCache 里面。

这里在将数据缓存之前会先在 4 处判断缓存是否可用。这里使用到了 `isCacheable` 这个字段。通过查看源码我们可以追踪到这个字段最初传入的位置是在 `RequestOptions` 里面。也就是说，这个字段是针对一次请求的，我们可以在构建 Glide 请求的时候通过 `apply()` 设置这个参数的值（这个字段默认是 `true`，也就是默认是启用内存缓存的）。

```java
  Glide.with(getContext())
    .load("https://3-im.guokr.com/0lSlGxgGIQkSQVA_Ja0U3Gxo0tPNIxuBCIXElrbkhpEXBAAAagMAAFBO.png")
    .apply(RequestOptions.skipMemoryCacheOf(false)) // 不忽略内存缓存，即启用
    .into(getBinding().iv);
```

### 2.3 磁盘缓存

上面介绍了内存缓存，下面我们分析一下磁盘缓存。

正如我们最初的示例那样，我们可以通过在构建请求的时候指定缓存的策略。我们的图片加载请求会得到一个 `RequestOptions`，我们通过查看该类的代码也可以看出，默认的缓存策略是 `AUTOMATIC` 的。

这里的 `AUTOMATIC` 定义在 `DiskCacheStrategy` 中，除了 `AUTOMATIC` 还有其他几种缓存策略，那么它们之间又有什么区别呢？

1. `ALL`：既缓存原始图片，也缓存转换过后的图片；对于远程图片，缓存 `DATA` 和 `RESOURCE`；对于本地图片，只缓存 `RESOURCE`。
2. `AUTOMATIC` (默认策略)：尝试对本地和远程图片使用最佳的策略。当你加载远程数据（比如，从 `URL` 下载）时，`AUTOMATIC` 策略仅会存储未被你的加载过程修改过 (比如，变换、裁剪等) 的原始数据（`DATA`），因为下载远程数据相比调整磁盘上已经存在的数据要昂贵得多。对于本地数据，`AUTOMATIC` 策略则会仅存储变换过的缩略图（`RESOURCE`），因为即使你需要再次生成另一个尺寸或类型的图片，取回原始数据也很容易。
3. `DATA`：只缓存未被处理的文件。我的理解就是我们获得的 `stream`。它是不会被展示出来的，需要经过装载 `decode`，对图片进行压缩和转换，等等操作，得到最终的图片才能被展示。
4. `NONE`：表示不缓存任何内容。
5. `RESOURCE`：表示只缓存转换过后的图片（也就是经过decode，转化裁剪的图片）。

那么这些缓存的策略是在哪里使用到的呢？回顾上一篇文章，首先，我们是在 `DecodeJob` 的状态模式中用到了磁盘缓存策略：

```java
  private Stage getNextStage(Stage current) {
    switch (current) {
      case INITIALIZE:
        // 是否解码缓存的转换图片，就是只做过变换之后的缓存数据
        return diskCacheStrategy.decodeCachedResource() ? Stage.RESOURCE_CACHE : getNextStage(Stage.RESOURCE_CACHE);
      case RESOURCE_CACHE:
        // 是否解码缓存的原始数据，就是指缓存的未做过变换的数据
        return diskCacheStrategy.decodeCachedData() ? Stage.DATA_CACHE : getNextStage(Stage.DATA_CACHE);
      case DATA_CACHE:
        return onlyRetrieveFromCache ? Stage.FINISHED : Stage.SOURCE;
      case SOURCE:
      case FINISHED:
        return Stage.FINISHED;
      default:
        throw new IllegalArgumentException("Unrecognized stage: " + current);
    }
  }

  private DataFetcherGenerator getNextGenerator() {
    switch (stage) {
      case RESOURCE_CACHE:
        return new ResourceCacheGenerator(decodeHelper, this);
      case DATA_CACHE:
        return new DataCacheGenerator(decodeHelper, this);
      case SOURCE:
        return new SourceGenerator(decodeHelper, this);
      case FINISHED:
        return null;
      default:
        throw new IllegalStateException("Unrecognized stage: " + stage);
    }
  }
```

首先会根据当前所处的阶段 `current` 以及缓存策略判断应该使用哪个 `DataFetcherGenerator` 加载数据。我们分别来看一下它们：

首先是 `ResourceCacheGenerator`，它用来从缓存中得到变换之后数据。当从缓存中拿数据的时候会调用到它的 `startNext()` 方法如下。从下面的方法也可以看出，当从缓存中拿数据的时候会先在代码 1 处构建一个用于获取缓存数据 key。在构建这个 key 的时候传入了图片大小、变换等各种参数，即根据各种变换后的条件获取缓存数据。因此，这个类是用来获取变换之后的缓存数据的。

```java
  public boolean startNext() {
    List<Key> sourceIds = helper.getCacheKeys();
    if (sourceIds.isEmpty()) {
      return false;
    }
    List<Class<?>> resourceClasses = helper.getRegisteredResourceClasses();
    if (resourceClasses.isEmpty()) {
      if (File.class.equals(helper.getTranscodeClass())) {
        return false;
      }
    }
    while (modelLoaders == null || !hasNextModelLoader()) {
      resourceClassIndex++;
      if (resourceClassIndex >= resourceClasses.size()) {
        sourceIdIndex++;
        if (sourceIdIndex >= sourceIds.size()) {
          return false;
        }
        resourceClassIndex = 0;
      }

      Key sourceId = sourceIds.get(sourceIdIndex);
      Class<?> resourceClass = resourceClasses.get(resourceClassIndex);
      Transformation<?> transformation = helper.getTransformation(resourceClass);
      currentKey =
          new ResourceCacheKey( // 1 构建获取缓存信息的键
              helper.getArrayPool(),
              sourceId,
              helper.getSignature(),
              helper.getWidth(),
              helper.getHeight(),
              transformation,
              resourceClass,
              helper.getOptions());
      cacheFile = helper.getDiskCache().get(currentKey); // 2 从缓存中获取缓存信息
      if (cacheFile != null) {
        sourceKey = sourceId;
        modelLoaders = helper.getModelLoaders(cacheFile);
        modelLoaderIndex = 0;
      }
    }

    loadData = null;
    boolean started = false;
    while (!started && hasNextModelLoader()) {
      ModelLoader<File, ?> modelLoader = modelLoaders.get(modelLoaderIndex++); // 3 使用文件方式从缓存中读取缓存数据
      loadData = modelLoader.buildLoadData(cacheFile,
          helper.getWidth(), helper.getHeight(), helper.getOptions());
      if (loadData != null && helper.hasLoadPath(loadData.fetcher.getDataClass())) {
        started = true;
        loadData.fetcher.loadData(helper.getPriority(), this);
      }
    }

    return started;
  }
```

当找到了缓存的值之后会使用 `File` 类型的 `ModelLoader` 加载数据。这个比较容易理解，因为数据存在磁盘上面，需要用文件的方式打开。

另外，我们再关注下 2 处的代码，它会使用 `helper` 的 `getDiskCache()` 方法获取 `DiskCache` 对象。我们一直追踪这个对象就会找到一个名为 `DiskLruCacheWrapper` 的类，它内部包装了 `DiskLruCache`。所以，最终从磁盘加载数据是使用 `DiskLruCache` 来实现的。对于最终使用 `DiskLruCache` 获取数据的逻辑我们不进行说明了，它的逻辑并不复杂，都是单纯的文件读写，只是设计了一套缓存的规则。

上面是从磁盘读取数据的，那么数据又是在哪里向磁盘缓存数据的呢？

在之前的文章中我们也分析过这部分内容，即当从网络中打开输入流之后会回到 `DecodeJob` 中，进入下一个阶段，并再次调用 `SourceGenerator` 的 `startNext()` 方法。此时会进入到 `cacheData()` 方法，并将数据缓存到磁盘上：

```java
  private void cacheData(Object dataToCache) {
    long startTime = LogTime.getLogTime();
    try {
      Encoder<Object> encoder = helper.getSourceEncoder(dataToCache);
      DataCacheWriter<Object> writer =
          new DataCacheWriter<>(encoder, dataToCache, helper.getOptions());
      originalKey = new DataCacheKey(loadData.sourceKey, helper.getSignature());
      helper.getDiskCache().put(originalKey, writer); // 将数据缓存到磁盘上面
    } finally {
      loadData.fetcher.cleanup();
    }

    sourceCacheGenerator =
        new DataCacheGenerator(Collections.singletonList(loadData.sourceKey), helper, this);
  }
```

然后构建一个 `DataCacheGenerator` 再从磁盘上面读取出缓存的数据，显示到控件上面。

还有一个问题，从上文中我们也可以看出 Glide 在进行缓存的时候可以缓存转换之后的数据，也可以缓存原始的数据。我们可以通过构建的用于获取缓存的键看出这一点：在 `ResourceCacheGenerator` 中获取转换之后的缓存数据的时候，我们使用 `ResourceCacheKey` 并传入了各种参数构建了缓存的键；在将数据存储到磁盘上面的时候我们使用的是 `DataCacheKey`，并且没有传入那么多参数。这说明获取的和存储的并不是同一份数据，那么转换之后的数据是在哪里缓存的呢？

我们通过查找类 `ResourceCacheKey` 将位置定位在了 `DecodeJob` 的 `onResourceDecoded()` 方法中：

```java
  <Z> Resource<Z> onResourceDecoded(DataSource dataSource, Resource<Z> decoded) {
    // ... 略

    Resource<Z> result = transformed;
    boolean isFromAlternateCacheKey = !decodeHelper.isSourceKey(currentSourceKey);
    if (diskCacheStrategy.isResourceCacheable(isFromAlternateCacheKey, dataSource,
        encodeStrategy)) {
      if (encoder == null) {
        throw new Registry.NoResultEncoderAvailableException(transformed.get().getClass());
      }
      final Key key;
      // 根据缓存的此略使用不同的缓存的键
      switch (encodeStrategy) {
        case SOURCE:
          key = new DataCacheKey(currentSourceKey, signature);
          break;
        case TRANSFORMED:
          key =
              new ResourceCacheKey(
                  decodeHelper.getArrayPool(),
                  currentSourceKey,
                  signature,
                  width,
                  height,
                  appliedTransformation,
                  resourceSubClass,
                  options);
          break;
        default:
          throw new IllegalArgumentException("Unknown strategy: " + encodeStrategy);
      }

      LockedResource<Z> lockedResult = LockedResource.obtain(transformed);
      // 将缓存的键和数据信息设置到 deferredEncodeManager 中，随后会将其缓存到磁盘上面
      deferredEncodeManager.init(key, encoder, lockedResult);
      result = lockedResult;
    }
    return result;
  }
```

显然，这里会根据缓存的策略构建两种不同的 key，并将其传入到 `deferredEncodeManager` 中。然后将会在 `DecodeJob` 的 `notifyEncodeAndRelease()` 方法中调用 `deferredEncodeManager` 的 `encode()` 方法将数据缓存到磁盘上:

```java
    void encode(DiskCacheProvider diskCacheProvider, Options options) {
      try {
        // 将数据缓存到磁盘上面
        diskCacheProvider.getDiskCache().put(key,
            new DataCacheWriter<>(encoder, toEncode, options));
      } finally {
        toEncode.unlock();
      }
    }
```

以上就是 Glide 的磁盘缓存的实现原理。

### 3、总结

在这篇文中我们在之前的两篇文章的基础之上分析了 Glide 的缓存的实现原理。

首先 Glide 存在两种内存缓存，一个基于弱引用的，一个是基于 LruCache 的。两者存在一些不同，在文中我们已经总结了这部分内容。

然后，我们分析了 Glide 的磁盘缓存的实现原理。Glide 的磁盘缓存使用了策略模式，存在 4 种既定的缓存策略。Glide 不仅可以原始的数据缓存到磁盘上面，还可以将做了转换之后的数据缓存到磁盘上面。它们会基于自身的缓存方式构建不同的 key 然后底层使用 DiskLruCache 从磁盘种获取数据。这部分的核心代码在 `DecodeJob` 和三个 `DataFetcherGenerator` 中。

以上就是 Glide 缓存的所有实现原理。
