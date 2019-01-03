# Glide 系列-2：主流程源码分析（4.8.0）

Glide 是 Android 端比较常用的图片加载框架，这里我们就不再介绍它的基础的使用方式。你可以通过查看其官方文档学习其基础使用。这里，我们给出一个 Glide 的最基本的使用示例，并以此来研究这个整个过程发生了什么：

```java
Glide.with(fragment).load(myUrl).into(imageView);
```

上面的代码虽然简单，但是整个执行过程涉及许多类，其流程也比较复杂。为了更清楚地说明这整个过程，我们将 Glide 的图片加载按照调用的时间关系分成了下面几个部分：

1. `with()` 方法的执行过程
2. `load()` 方法的执行过程
3. `into()` 方法的执行过程
    1. 阶段1：开启 `DecodeJob` 的过程
    2. 阶段2：打开网络流的过程
    3. 阶段3：将输入流转换为 `Drawable` 的过程
    4. 阶段4：将 `Drawable` 展示到 `ImageView` 的过程

即按照上面的示例代码，先分成 `with()`、`load()` 和 `into()` 三个过程，而 `into()` 过程又被细化成四个阶段。

下面我们就按照上面划分的过程来分别介绍一下各个过程中都做了哪些操作。

## 1、with() 方法的执行过程

### 1.1 实例化单例的 Glide 的过程

当调用了 Glide 的 `with()` 方法的时候会得到一个 `RequestManager` 实例。`with()` 有多个重载方法，我们可以使用 `Activity` 或者 `Fragment` 等来获取 `Glide` 实例。它们最终都会调用下面这个方法来完成最终的操作：

```java
public static RequestManager with(Context context) {
    return getRetriever(context).get(context);
}
```

在 `getRetriever()` 方法内部我们会先使用 `Glide` 的 `get()` 方法获取一个单例的 Glide 实例，然后从该 Glide 实例中得到一个 `RequestManagerRetriever`:

```java
private static RequestManagerRetriever getRetriever(Context context) {
    return Glide.get(context).getRequestManagerRetriever();
}
```

这里调用了 Glide 的 `get()` 方法，它最终会调用 `initializeGlide()` 方法实例化一个**单例**的 `Glide` 实例。在之前的文中我们已经介绍了这个方法。它主要用来从注解和 Manifest 中获取 GlideModule，并根据各 GlideModule 中的方法对 Glide 进行自定义：

[《Glide 系列-1：预热、Glide 的常用配置方式及其原理》](Glide系列：Glide的配置和使用方式.md)

下面的方法中需要传入一个 `GlideBuilder` 实例。很明显这是一种构建者模式的应用，我们可以使用它的方法来实现对 Glide 的个性化配置：

```java
private static void initializeGlide(Context context, GlideBuilder builder) {

    // ... 各种操作，略

    // 赋值给静态的单例实例
    Glide.glide = glide;
}
```

最终 Glide 实例由 `GlideBuilder` 的 `build()` 方法构建完毕。它会直接调用 Glide 的构造方法来完成 Glide 的创建。在该构造方法中会将各种类型的图片资源及其对应的加载类的映射关系注册到 Glide 中，你可以阅读源码了解这部分内容。

### 1.2 Glide 的生命周期管理

在 `with()` 方法的执行过程还有一个重要的地方是 Glide 的生命周期管理。因为当我们正在进行图片加载的时候，Fragment 或者 Activity 的生命周期可能已经结束了，所以，我们需要对 Glide 的生命周期进行管理。

Glide 对这部分内容的处理也非常巧妙，它使用没有 UI 的 Fragment 来管理 Glide 的生命周期。这也是一种非常常用的生命周期管理方式，比如 `RxPermission` 等框架都使用了这种方式。你可以通过下面的示例来了解它的作用原理：

[示例代码：使用 Fragment 管理 onActivityResult()](https://github.com/Shouheng88/Android-references/tree/master/advanced/src/main/java/me/shouheng/advanced/callback)

在 `with()` 方法中，当我们调用了 `RequestManagerRetriever` 的 `get()` 方法之后，会根据 Context 的类型调用 `get()` 的各个重载方法。

```java
  public RequestManager get(@NonNull Context context) {
    if (context == null) {
      throw new IllegalArgumentException("You cannot start a load on a null Context");
    } else if (Util.isOnMainThread() && !(context instanceof Application)) {
      if (context instanceof FragmentActivity) {
        return get((FragmentActivity) context);
      } else if (context instanceof Activity) {
        return get((Activity) context);
      } else if (context instanceof ContextWrapper) {
        return get(((ContextWrapper) context).getBaseContext());
      }
    }

    return getApplicationManager(context);
  }
```

我们以 Activity 为例。如下面的方法所示，当当前位于后台线程的时候，会使用 Application 的 Context 获取 `RequestManager`，否则会使用无 UI 的 Fragment 进行管理：

```java
  public RequestManager get(@NonNull Activity activity) {
    if (Util.isOnBackgroundThread()) {
      return get(activity.getApplicationContext());
    } else {
      assertNotDestroyed(activity);
      android.app.FragmentManager fm = activity.getFragmentManager();
      return fragmentGet(
          activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
    }
  }
```

然后就调用到了 `fragmentGet()` 方法。这里我们从 `RequestManagerFragment` 中通过 `getGlideLifecycle()` 获取到了 `Lifecycle` 对象。`Lifecycle` 对象提供了一系列的、针对 Fragment 生命周期的方法。它们将会在 Fragment 的各个生命周期方法中被回调。

```java
  private RequestManager fragmentGet(Context context, FragmentManager fm, 
    Fragment parentHint, boolean isParentVisible) {
    RequestManagerFragment current = getRequestManagerFragment(fm, parentHint, isParentVisible);
    RequestManager requestManager = current.getRequestManager();
    if (requestManager == null) {
      Glide glide = Glide.get(context);
      requestManager =
          factory.build(
              glide, current.getGlideLifecycle(), current.getRequestManagerTreeNode(), context);
      current.setRequestManager(requestManager);
    }
    return requestManager;
  }
```

然后，我们将该 `Lifecycle` 传入到 `RequestManager` 中，以 `RequestManager` 中的两个方法为例，`RequestManager` 会对 `Lifecycle` 进行监听，从而达到了对 Fragment 的生命周期进行监听的目的：

```java
  public void onStart() {
    resumeRequests();
    targetTracker.onStart();
  }

  public void onStop() {
    pauseRequests();
    targetTracker.onStop();
  }
```

## 2、load() 方法的执行过程

当我们拿到了 `RequestManager` 之后就可以使用它来调用 `load()` 方法了。在我们的实例中传入的是一个 url，所以会调用下面的这个方法：

    public RequestBuilder<Drawable> load(@Nullable String string) {
        return asDrawable().load(string);
    }

另外，`load()` 方法也是重载的，我们可以传入包括 Bitmap, Drawable, Uri 和 String 等在内的多种资源类型。

当我们调用完了上面的方法之后最终会调用下面的方法来获取一个 `RequestBuilder` 对象。在上面的 `asDrawable()` 方法中，我们传入的 `resourceClass` 是 `Drawable`，所以就最终得到了 `RequestBuilder<Drawable>`：

    public <ResourceType> RequestBuilder<ResourceType> as(@NonNull Class<ResourceType> resourceClass) {
        return new RequestBuilder<>(glide, this, resourceClass, context);
    }

然后，我们继续调用 `RequestBuilder<Drawable>` 的 `load(Object)` 方法来继续构建图片加载的请求：

    public RequestBuilder<TranscodeType> load(@Nullable Object model) {
        return loadGeneric(model);
    }

    @NonNull
    private RequestBuilder<TranscodeType> loadGeneric(@Nullable Object model) {
        this.model = model;
        isModelSet = true;
        return this;
    }

`load()` 方法里又调用了 `loadGeneric()` 方法。它内部把要加载的图片对应的数据模型赋值给局部变量 `model` 之后就返回了。这样，我们就到达了 `into()` 方法。 

### into()

`into()` 方法也定义在 `RequestBuilder` 内部：

    public ViewTarget<ImageView, TranscodeType> into(@NonNull ImageView view) {
        Util.assertMainThread();
        Preconditions.checkNotNull(view);

        RequestOptions requestOptions = this.requestOptions;
        if (!requestOptions.isTransformationSet() && requestOptions.isTransformationAllowed() && view.getScaleType() != null) {
            switch (view.getScaleType()) {
                case CENTER_CROP:
                    requestOptions = requestOptions.clone().optionalCenterCrop();
                    break;
                // ... 根据图片的 scaleType 属性对请求做调整，类似与上面
            }
        }

        return into(glideContext.buildImageViewTarget(view, transcodeClass), null, requestOptions);
    }

RequestManager 中的 into() 具有多个重载方法，它们最终都会调用到下面的 into() 方法：

    private <Y extends Target<TranscodeType>> Y into(@NonNull Y target,
            RequestListener<TranscodeType> targetListener,
            BaseRequestOptions<?> options) {
        Util.assertMainThread();
        Preconditions.checkNotNull(target);
        if (!isModelSet) {
            throw new IllegalArgumentException("You must call #load() before calling #into()");
        }

        Request request = buildRequest(target, targetListener, options);

        Request previous = target.getRequest();
        if (request.isEquivalentTo(previous) && !isSkipMemoryCacheWithCompletePreviousRequest(options, previous)) {
            request.recycle();
            if (!Preconditions.checkNotNull(previous).isRunning()) {
                previous.begin();
            }
            return target;
        }

        requestManager.clear(target);
        target.setRequest(request);
        requestManager.track(target, request);

        return target;
    }

上面的 `buildRequest()` 最终会调用到下面的方法来构建一个请求对象，
	
    private Request buildRequestRecursive(
            Target<TranscodeType> target,
            @Nullable RequestListener<TranscodeType> targetListener,
            @Nullable RequestCoordinator parentCoordinator,
            TransitionOptions<?, ? super TranscodeType> transitionOptions,
            Priority priority,
            int overrideWidth,
            int overrideHeight,
            BaseRequestOptions<?> requestOptions) {

        // 根据我们是否设置了发生错误时的处理策略来构建一个错误请求
        ErrorRequestCoordinator errorRequestCoordinator = null;
        if (errorBuilder != null) {
            errorRequestCoordinator = new ErrorRequestCoordinator(parentCoordinator);
            parentCoordinator = errorRequestCoordinator;
        }

		// 构建主要的请求
        Request mainRequest = buildThumbnailRequestRecursive(/* 各种参数 */);

        if (errorRequestCoordinator == null) {
            return mainRequest;
        }

        int errorOverrideWidth = errorBuilder.getOverrideWidth();
        int errorOverrideHeight = errorBuilder.getOverrideHeight();
        if (Util.isValidDimensions(overrideWidth, overrideHeight)
                && !errorBuilder.isValidOverride()) {
            errorOverrideWidth = requestOptions.getOverrideWidth();
            errorOverrideHeight = requestOptions.getOverrideHeight();
        }

        Request errorRequest = errorBuilder.buildRequestRecursive(/* 各种参数 */);
        errorRequestCoordinator.setRequests(mainRequest, errorRequest);
        return errorRequestCoordinator;
    }

下面的方法是用来构建主请求的，虽然看上去比较长，但是内部并没有做太多的核心的逻辑，无非就是根据当前是否设置了 `Thumbnail` (缩略图) 来决定如何构建一个请求，但不论哪种方式都会调用到 `obtainRequest()` 方法来完成最终的请求的构建：

    private Request buildThumbnailRequestRecursive(
            Target<TranscodeType> target,
            RequestListener<TranscodeType> targetListener,
            @Nullable RequestCoordinator parentCoordinator,
            TransitionOptions<?, ? super TranscodeType> transitionOptions,
            Priority priority,
            int overrideWidth,
            int overrideHeight,
            BaseRequestOptions<?> requestOptions) {
		// 这里根据我们是否设置了 Thumbnail，分成两种情况来构建请求
        if (thumbnailBuilder != null) {
            TransitionOptions<?, ? super TranscodeType> thumbTransitionOptions = thumbnailBuilder.transitionOptions;

            if (thumbnailBuilder.isDefaultTransitionOptionsSet) {
                thumbTransitionOptions = transitionOptions;
            }

            Priority thumbPriority = thumbnailBuilder.isPrioritySet()
                    ? thumbnailBuilder.getPriority() : getThumbnailPriority(priority);

            int thumbOverrideWidth = thumbnailBuilder.getOverrideWidth();
            int thumbOverrideHeight = thumbnailBuilder.getOverrideHeight();
            if (Util.isValidDimensions(overrideWidth, overrideHeight)
                    && !thumbnailBuilder.isValidOverride()) {
                thumbOverrideWidth = requestOptions.getOverrideWidth();
                thumbOverrideHeight = requestOptions.getOverrideHeight();
            }

            ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
            Request fullRequest = obtainRequest(/* 各种参数 */);
            isThumbnailBuilt = true;

            Request thumbRequest = thumbnailBuilder.buildRequestRecursive(/* 各种参数 */);
            isThumbnailBuilt = false;
            coordinator.setRequests(fullRequest, thumbRequest);
            return coordinator;
        } else if (thumbSizeMultiplier != null) {
            ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
            Request fullRequest = obtainRequest(/* 各种参数 */);
            BaseRequestOptions<?> thumbnailOptions = requestOptions.clone().sizeMultiplier(thumbSizeMultiplier);

            Request thumbnailRequest = obtainRequest(/* 各种参数 */);

            coordinator.setRequests(fullRequest, thumbnailRequest);
            return coordinator;
        } else {
		    // 当没有设置过 Thumbnail 的时候就会直接调用下面的这个方法了
            return obtainRequest(/* 各种参数 */);
        }
    }
	
显然下面的方法中使用了 `SingleRequest` 的静态 `obtain()` 方法来构建一个请求。

    private Request obtainRequest(
            Target<TranscodeType> target,
            RequestListener<TranscodeType> targetListener,
            BaseRequestOptions<?> requestOptions,
            RequestCoordinator requestCoordinator,
            TransitionOptions<?, ? super TranscodeType> transitionOptions,
            Priority priority,
            int overrideWidth,
            int overrideHeight) {
        return SingleRequest.obtain(/* 各种参数 */);
    }

而在 `SingleRequest` 的 `obtain()` 方法中会先尝试从请求的池中取出一个请求，当请求不存在的时候就会实例化一个 `SingleRequest`，然后调用它的 `init()` 方法把各种参数赋值进去：

    public static <R> SingleRequest<R> obtain(/* 各种参数 */) {
        SingleRequest<R> request = (SingleRequest<R>) POOL.acquire();
        if (request == null) {
            request = new SingleRequest<>();
        }
        request.init(/* 各种参数 */);
        return request;
    }

这里的 Pool 有两个，一个是用 simple() 静态方法创建的，在创建它的时候内部会 new 出一个 SimplePool. 而所谓的 SimplePool 本质上就是在内部维护了一个数组，这里的 simple() 静态方法返回的是 FactoryPool 实例。FactoryPool 只是对 SimplePool 进行了一层封装。所以，本质上这里的池 POOL 就是一个数组。

    private static final Pools.Pool<SingleRequest<?>> POOL = FactoryPools.simple(150,
            new FactoryPools.Factory<SingleRequest<?>>() {
                @Override
                public SingleRequest<?> create() {
                    return new SingleRequest<Object>();
                }
            });

这样我们分析了请求的构建的过程。那么我们再回到之前的位置来看一下，当请求被构建完成之后又是如何来使用请求来获取图片资源并展示的。

当得到了请求之后会调用 RequestManager 的 track() 方法。在该方法内部又会调用 RequestTracker 的 runRequest() 方法。它首先会把请求加入到一个 Set 中，然后判断Glide当前是不是处理暂停状态，如果没有处于暂停状态就会调用请求的 `begin()` 方法来开始请求：

    requestManager.track(target, request);
	
    void track(@NonNull Target<?> target, @NonNull Request request) {
        targetTracker.track(target);
        requestTracker.runRequest(request);
    }

	public void runRequest(@NonNull Request request) {
        requests.add(request);
        if (!isPaused) {
            request.begin();
        } else {
            request.clear();
            pendingRequests.add(request);
        }
    }

我们这里的请求最终是 SingleRequst，所以，我们可以得到其 begin() 方法如下：

    public void begin() {
        assertNotCallingCallbacks();
        stateVerifier.throwIfRecycled();
        if (model == null) {
            if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
                width = overrideWidth;
                height = overrideHeight;
            }
            int logLevel = getFallbackDrawable() == null ? Log.WARN : Log.DEBUG;
            onLoadFailed(new GlideException("Received null model"), logLevel);
            return;
        }

        if (status == Status.RUNNING) {
            throw new IllegalArgumentException("Cannot restart a running request");
        }

        // 如果我们在完成之后重新启动（通常通过诸如notifyDataSetChanged之类的东西，
        // 在相同的目标或视图中启动相同的请求），我们可以简单地使用我们上次检索的资源和大小
        // 并跳过获取新的大小。这意味着想要重新启动负载因为期望视图大小已更改的用户
        // 需要在开始新加载之前明确清除视图 (View) 或目标 (Target)。
        if (status == Status.COMPLETE) {
            onResourceReady(resource, DataSource.MEMORY_CACHE);
            return;
        }

        status = Status.WAITING_FOR_SIZE;
        if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
            onSizeReady(overrideWidth, overrideHeight);
        } else {
            target.getSize(this);
        }

        if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
                && canNotifyStatusChanged()) {
            // 使用占位图
            target.onLoadStarted(getPlaceholderDrawable());
        }
    }

当 model 为空时会调用 `onLoadFailed()` 方法，

#### Q：model 何时传入？

    private void onLoadFailed(GlideException e, int maxLogLevel) {
        loadStatus = null;
        status = Status.FAILED;

        isCallingCallbacks = true;
        try {
            boolean anyListenerHandledUpdatingTarget = false;
            if (requestListeners != null) {
                for (RequestListener<R> listener : requestListeners) {
                    anyListenerHandledUpdatingTarget |=
                            listener.onLoadFailed(e, model, target, isFirstReadyResource());
                }
            }
            anyListenerHandledUpdatingTarget |= targetListener != null
                && targetListener.onLoadFailed(e, model, target, isFirstReadyResource());

            if (!anyListenerHandledUpdatingTarget) {
                setErrorPlaceholder();
            }
        } finally {
            isCallingCallbacks = false;
        }

        notifyLoadFailed();
    }

    private void setErrorPlaceholder() {
        if (!canNotifyStatusChanged()) {
            return;
        }

        Drawable error = null;
        if (model == null) {
            error = getFallbackDrawable();
        }
        if (error == null) {
            error = getErrorDrawable();
        }
        if (error == null) {
            error = getPlaceholderDrawable();
        }
        target.onLoadFailed(error);
    }

    public void onLoadFailed(@Nullable Drawable errorDrawable) {
        super.onLoadFailed(errorDrawable);
        setResourceInternal(null);
        setDrawable(errorDrawable);
    }

    // 最终在 ImageViewTarget 中将错误图片的 Drawable 设置到 ImageView 上
    public void setDrawable(Drawable drawable) {
        view.setImageDrawable(drawable);
    }

#### onSizeReady(int, int)

    public void onSizeReady(int width, int height) {
        stateVerifier.throwIfRecycled();
        if (status != Status.WAITING_FOR_SIZE) {
            return;
        }
        status = Status.RUNNING;

        float sizeMultiplier = requestOptions.getSizeMultiplier();
        this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
        this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

        loadStatus = engine.load(/* 各种参数 */);

        if (status != Status.RUNNING) {
            loadStatus = null;
        }
    }

根据指定的参数开始加载图片，必须在主线程中调用。

    public <R> LoadStatus load(/* 各种参数 */) {
        Util.assertMainThread();
        long startTime = VERBOSE_IS_LOGGABLE ? LogTime.getLogTime() : 0;

        EngineKey key = keyFactory.buildKey(model, signature, width, height, transformations,
                resourceClass, transcodeClass, options);

        EngineResource<?> active = loadFromActiveResources(key, isMemoryCacheable);
        if (active != null) {
            cb.onResourceReady(active, DataSource.MEMORY_CACHE);
            return null;
        }

        EngineResource<?> cached = loadFromCache(key, isMemoryCacheable);
        if (cached != null) {
            cb.onResourceReady(cached, DataSource.MEMORY_CACHE);
            if (VERBOSE_IS_LOGGABLE) {
                logWithTimeAndKey("Loaded resource from cache", startTime, key);
            }
            return null;
        }

        EngineJob<?> current = jobs.get(key, onlyRetrieveFromCache);
        if (current != null) {
            current.addCallback(cb);
            return new LoadStatus(cb, current);
        }

        EngineJob<R> engineJob =
                engineJobFactory.build(
                        key,
                        isMemoryCacheable,
                        useUnlimitedSourceExecutorPool,
                        useAnimationPool,
                        onlyRetrieveFromCache);

        DecodeJob<R> decodeJob =
                decodeJobFactory.build(
                        glideContext,
                        model,
                        key,
                        signature,
                        width,
                        height,
                        resourceClass,
                        transcodeClass,
                        priority,
                        diskCacheStrategy,
                        transformations,
                        isTransformationRequired,
                        isScaleOnlyOrNoTransform,
                        onlyRetrieveFromCache,
                        options,
                        engineJob);

        jobs.put(key, engineJob);

        engineJob.addCallback(cb);
        engineJob.start(decodeJob);

        return new LoadStatus(cb, engineJob);
    }

EngineJob 是一个普通的类，DecodeJob 是一个 Runnable，当调用了 EngineJob 的 start() 方法之后会将 DecodeJob 放进线程池当中进行执行。

    public void start(DecodeJob<R> decodeJob) {
        this.decodeJob = decodeJob;
        GlideExecutor executor = decodeJob.willDecodeFromCache()
                ? diskCacheExecutor
                : getActiveSourceExecutor();
        executor.execute(decodeJob);
    }

所以，如果想要找到加载资源和解码的逻辑，就应该查看 DecodeJob 的 run() 方法：

    public void run() {
        GlideTrace.beginSectionFormat("DecodeJob#run(model=%s)", model);
        DataFetcher<?> localFetcher = currentFetcher;
        try {
            if (isCancelled) {
                notifyFailed();
                return;
            }
            // 主要是 runWrapped()
            runWrapped();
        } catch (Throwable t) {
            if (stage != Stage.ENCODE) {
                throwables.add(t);
                notifyFailed();
            }
            if (!isCancelled) {
                throw t;
            }
        } finally {
            if (localFetcher != null) {
                localFetcher.cleanup();
            }
            GlideTrace.endSection();
        }
    }

下面的方法类似于状态模式，会根据 runReason 执行不同的方法，

    private void runWrapped() {
        switch (runReason) {
            case INITIALIZE:
                stage = getNextStage(Stage.INITIALIZE);
                currentGenerator = getNextGenerator();
                runGenerators();
                break;
            case SWITCH_TO_SOURCE_SERVICE:
                runGenerators();
                break;
            case DECODE_DATA:
                decodeFromRetrievedData();
                break;
            default:
                throw new IllegalStateException("Unrecognized run reason: " + runReason);
        }
    }

getNextGenerator() 方法会返回 DataFetcherGenerator 对象：

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

然后在 runGenerators() 方法中调用 DataFetcherGenerator 的 startNext() 方法：

    public boolean startNext() {
        if (dataToCache != null) {
            Object data = dataToCache;
            dataToCache = null;
            cacheData(data);
        }

        if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
            return true;
        }
        sourceCacheGenerator = null;

        loadData = null;
        boolean started = false;
        while (!started && hasNextModelLoader()) {
            loadData = helper.getLoadData().get(loadDataListIndex++);
            if (loadData != null
                    && (helper.getDiskCacheStrategy().isDataCacheable(loadData.fetcher.getDataSource())
                    || helper.hasLoadPath(loadData.fetcher.getDataClass()))) {
                started = true;
                loadData.fetcher.loadData(helper.getPriority(), this);
            }
        }
        return started;
    }

这里调用了一个 getLoadData() 方法，它的定义如下，它会从我们之前注册的 ModelLoader 中取出对应的 ModelLoader，这样就会找到 HttpGlideUrlLoader：

    List<LoadData<?>> getLoadData() {
        if (!isLoadDataSet) {
            isLoadDataSet = true;
            loadData.clear();
            List<ModelLoader<Object, ?>> modelLoaders = glideContext.getRegistry().getModelLoaders(model);
            // noinspection ForLoopReplaceableByForEach to improve perf
            for (int i = 0, size = modelLoaders.size(); i < size; i++) {
                ModelLoader<Object, ?> modelLoader = modelLoaders.get(i);
                LoadData<?> current =
                        modelLoader.buildLoadData(model, width, height, options);
                if (current != null) {
                    loadData.add(current);
                }
            }
        }
        return loadData;
    }

然后会在获取 fetcher 的时候得到 HttpUrlFetcher，

所以，最终从网络当中加载数据的逻辑 HttpUrlFetcher：

    private InputStream loadDataWithRedirects(URL url, int redirects, URL lastUrl, Map<String, String> headers) throws IOException {
        if (redirects >= MAXIMUM_REDIRECTS) {
            throw new HttpException("Too many (> " + MAXIMUM_REDIRECTS + ") redirects!");
        } else {
            try {
                if (lastUrl != null && url.toURI().equals(lastUrl.toURI())) {
                    throw new HttpException("In re-direct loop");
                }
            } catch (URISyntaxException e) {
                // Do nothing, this is best effort.
            }
        }

        urlConnection = connectionFactory.build(url);
        for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
            urlConnection.addRequestProperty(headerEntry.getKey(), headerEntry.getValue());
        }
        urlConnection.setConnectTimeout(timeout);
        urlConnection.setReadTimeout(timeout);
        urlConnection.setUseCaches(false);
        urlConnection.setDoInput(true);

        urlConnection.setInstanceFollowRedirects(false);

        urlConnection.connect();
        stream = urlConnection.getInputStream();
        if (isCancelled) {
            return null;
        }
        final int statusCode = urlConnection.getResponseCode();
        if (isHttpOk(statusCode)) {
            return getStreamForSuccessfulRequest(urlConnection);
        } else if (isHttpRedirect(statusCode)) {
            String redirectUrlString = urlConnection.getHeaderField("Location");
            if (TextUtils.isEmpty(redirectUrlString)) {
                throw new HttpException("Received empty or null redirect url");
            }
            URL redirectUrl = new URL(url, redirectUrlString);
            cleanup();
            return loadDataWithRedirects(redirectUrl, redirects + 1, url, headers);
        } else if (statusCode == INVALID_STATUS_CODE) {
            throw new HttpException(statusCode);
        } else {
            throw new HttpException(urlConnection.getResponseMessage(), statusCode);
        }
    }


Q：逻辑，找分叉：按照 with() 的参数判断算是一个分叉，按照各种数据源选择不同的 Fetcher 是另一个分叉……按照这种方式把发生分歧的地方找出来！！



在 Glide 构造方法中会将图片资源的类型与对应的Fetcher 做映射：

   .append(GlideUrl.class, InputStream.class, new HttpGlideUrlLoader.Factory())

HttpGlideUrlLoader.Factory 的 build 方法会返回 HttpGlideUrlLoader

    public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory multiFactory) {
        return new HttpGlideUrlLoader(modelCache);
    }

HttpGlideUrlLoader 的 buildLoadData() 方法中会实例化要给 HttpUrlFetcher

    public LoadData<InputStream> buildLoadData(GlideUrl model, int width, int height, Options options) {
        GlideUrl url = model;
        if (modelCache != null) {
            url = modelCache.get(model, 0, 0);
            if (url == null) {
                modelCache.put(model, 0, 0, model);
                url = model;
            }
        }
        int timeout = options.get(TIMEOUT);
        return new LoadData<>(url, new HttpUrlFetcher(url, timeout));
    }

    public synchronized <T> Encoder<T> getEncoder(@NonNull Class<T> dataClass) {
        for (Entry<?> entry : encoders) {
            if (entry.handles(dataClass)) {
                return (Encoder<T>) entry.encoder;
            }
        }
        return null;
    }



## 拿到图片的输入流之后的回调

##### SourceGenerator#onDataReady()

  @Override
  public void onDataReady(Object data) {
    DiskCacheStrategy diskCacheStrategy = helper.getDiskCacheStrategy();
    if (data != null && diskCacheStrategy.isDataCacheable(loadData.fetcher.getDataSource())) {
      dataToCache = data;
      cb.reschedule();
    } else {
      cb.onDataFetcherReady(loadData.sourceKey, data, loadData.fetcher,
          loadData.fetcher.getDataSource(), originalKey);
    }
  }

##### DecodeJob#onDataFetcherReady()

  public void onDataFetcherReady(Key sourceKey, Object data, DataFetcher<?> fetcher, DataSource dataSource, Key attemptedKey) {
    this.currentSourceKey = sourceKey;
    this.currentData = data;
    this.currentFetcher = fetcher;
    this.currentDataSource = dataSource;
    this.currentAttemptingKey = attemptedKey;
    if (Thread.currentThread() != currentThread) {
      runReason = RunReason.DECODE_DATA;
      callback.reschedule(this);
    } else {
      GlideTrace.beginSection("DecodeJob.decodeFromRetrievedData");
      try {
        decodeFromRetrievedData();
      } finally {
        GlideTrace.endSection();
      }
    }
  }

##### DecodeJob#decodeFromRetrievedData()

  private void decodeFromRetrievedData() {
    Resource<R> resource = null;
    try {
      resource = decodeFromData(currentFetcher, currentData, currentDataSource);
    } catch (GlideException e) {
      e.setLoggingDetails(currentAttemptingKey, currentDataSource);
      throwables.add(e);
    }
    if (resource != null) {
      notifyEncodeAndRelease(resource, currentDataSource);
    } else {
      runGenerators();
    }
  }

##### DecodeJob#decodeFromData()

  private <Data> Resource<R> decodeFromData(DataFetcher<?> fetcher, Data data,
      DataSource dataSource) throws GlideException {
    try {
      if (data == null) {
        return null;
      }
      Resource<R> result = decodeFromFetcher(data, dataSource);
      return result;
    } finally {
      fetcher.cleanup();
    }
  }

##### DecodeJob#decodeFromFetcher()

  private <Data> Resource<R> decodeFromFetcher(Data data, DataSource dataSource)
      throws GlideException {
    LoadPath<Data, ?, R> path = decodeHelper.getLoadPath((Class<Data>) data.getClass());
    return runLoadPath(data, dataSource, path);
  }

##### DecodeJob#runLoadPath()

  private <Data, ResourceType> Resource<R> runLoadPath(Data data, DataSource dataSource,
      LoadPath<Data, ResourceType, R> path) throws GlideException {
    Options options = getOptionsWithHardwareConfig(dataSource);
    DataRewinder<Data> rewinder = glideContext.getRegistry().getRewinder(data);
    try {
      return path.load(rewinder, options, width, height, new DecodeCallback<ResourceType>(dataSource));
    } finally {
      rewinder.cleanup();
    }
  }

在这里我们传入了一个实例化的 DecodeCallback，它的作用是？

##### LoadPath#load()

  public Resource<Transcode> load(DataRewinder<Data> rewinder, @NonNull Options options, int width,
      int height, DecodePath.DecodeCallback<ResourceType> decodeCallback) throws GlideException {
    List<Throwable> throwables = Preconditions.checkNotNull(listPool.acquire());
    try {
      return loadWithExceptionList(rewinder, options, width, height, decodeCallback, throwables);
    } finally {
      listPool.release(throwables);
    }
  }

##### LoadPath#loadWithExceptionList()

  private Resource<Transcode> loadWithExceptionList(DataRewinder<Data> rewinder, Options options, 
      int width, int height, DecodePath.DecodeCallback<ResourceType> decodeCallback,
      List<Throwable> exceptions) throws GlideException {
    Resource<Transcode> result = null;
    for (int i = 0, size = decodePaths.size(); i < size; i++) {
      DecodePath<Data, ResourceType, Transcode> path = decodePaths.get(i);
      try {
        result = path.decode(rewinder, width, height, options, decodeCallback);
      } catch (GlideException e) {
        exceptions.add(e);
      }
      if (result != null) {
        break;
      }
    }
    // ... 
    return result;
  }

##### DecodePath#decode()

  public Resource<Transcode> decode(DataRewinder<DataType> rewinder, int width, int height,
      Options options, DecodeCallback<ResourceType> callback) throws GlideException {
    Resource<ResourceType> decoded = decodeResource(rewinder, width, height, options);
    Resource<ResourceType> transformed = callback.onResourceDecoded(decoded);
    return transcoder.transcode(transformed, options);
  }

##### DecodePath#decodeResource()

  private Resource<ResourceType> decodeResource(DataRewinder<DataType> rewinder, int width,
      int height, @NonNull Options options) throws GlideException {
    List<Throwable> exceptions = Preconditions.checkNotNull(listPool.acquire());
    try {
      return decodeResourceWithList(rewinder, width, height, options, exceptions);
    } finally {
      listPool.release(exceptions);
    }
  }

##### DecodePath#decodeResourceWithList()

  private Resource<ResourceType> decodeResourceWithList(DataRewinder<DataType> rewinder, int width,
      int height, @NonNull Options options, List<Throwable> exceptions) throws GlideException {
    Resource<ResourceType> result = null;
    for (int i = 0, size = decoders.size(); i < size; i++) {
      ResourceDecoder<DataType, ResourceType> decoder = decoders.get(i);
      try {
        DataType data = rewinder.rewindAndGet();
        if (decoder.handles(data, options)) {
          data = rewinder.rewindAndGet();
          result = decoder.decode(data, width, height, options);
        }
      } catch (IOException | RuntimeException | OutOfMemoryError e) {
        exceptions.add(e);
      }

      if (result != null) {
        break;
      }
    }

    return result;
  }

##### StreamBitmapDecoder#decode()

  public Resource<Bitmap> decode(InputStream source, int width, int height, Options options) throws IOException {
    final RecyclableBufferedInputStream bufferedStream;
    final boolean ownsBufferedStream;
    if (source instanceof RecyclableBufferedInputStream) {
      bufferedStream = (RecyclableBufferedInputStream) source;
      ownsBufferedStream = false;
    } else {
      bufferedStream = new RecyclableBufferedInputStream(source, byteArrayPool);
      ownsBufferedStream = true;
    }

    ExceptionCatchingInputStream exceptionStream = ExceptionCatchingInputStream.obtain(bufferedStream);

    MarkEnforcingInputStream invalidatingStream = new MarkEnforcingInputStream(exceptionStream);
    UntrustedCallbacks callbacks = new UntrustedCallbacks(bufferedStream, exceptionStream);
    try {
      return downsampler.decode(invalidatingStream, width, height, options, callbacks);
    } finally {
      exceptionStream.release();
      if (ownsBufferedStream) {
        bufferedStream.release();
      }
    }
  }

##### Downsampler#decode()

  public Resource<Bitmap> decode(InputStream is, int requestedWidth, int requestedHeight,
      Options options, DecodeCallbacks callbacks) throws IOException {
    byte[] bytesForOptions = byteArrayPool.get(ArrayPool.STANDARD_BUFFER_SIZE_BYTES, byte[].class);
    BitmapFactory.Options bitmapFactoryOptions = getDefaultOptions();
    bitmapFactoryOptions.inTempStorage = bytesForOptions;

    DecodeFormat decodeFormat = options.get(DECODE_FORMAT);
    DownsampleStrategy downsampleStrategy = options.get(DownsampleStrategy.OPTION);
    boolean fixBitmapToRequestedDimensions = options.get(FIX_BITMAP_SIZE_TO_REQUESTED_DIMENSIONS);
    boolean isHardwareConfigAllowed =
      options.get(ALLOW_HARDWARE_CONFIG) != null && options.get(ALLOW_HARDWARE_CONFIG);

    try {
      Bitmap result = decodeFromWrappedStreams(is, bitmapFactoryOptions,
          downsampleStrategy, decodeFormat, isHardwareConfigAllowed, requestedWidth,
          requestedHeight, fixBitmapToRequestedDimensions, callbacks);
      return BitmapResource.obtain(result, bitmapPool);
    } finally {
      releaseOptions(bitmapFactoryOptions);
      byteArrayPool.put(bytesForOptions);
    }
  }

##### Downsampler#decodeFromWrappedStreams()

  private Bitmap decodeFromWrappedStreams(InputStream is,
      BitmapFactory.Options options, DownsampleStrategy downsampleStrategy,
      DecodeFormat decodeFormat, boolean isHardwareConfigAllowed, int requestedWidth,
      int requestedHeight, boolean fixBitmapToRequestedDimensions,
      DecodeCallbacks callbacks) throws IOException {
    long startTime = LogTime.getLogTime();

    int[] sourceDimensions = getDimensions(is, options, callbacks, bitmapPool);
    int sourceWidth = sourceDimensions[0];
    int sourceHeight = sourceDimensions[1];
    String sourceMimeType = options.outMimeType;

    if (sourceWidth == -1 || sourceHeight == -1) {
      isHardwareConfigAllowed = false;
    }

    int orientation = ImageHeaderParserUtils.getOrientation(parsers, is, byteArrayPool);
    int degreesToRotate = TransformationUtils.getExifOrientationDegrees(orientation);
    boolean isExifOrientationRequired = TransformationUtils.isExifOrientationRequired(orientation);

    int targetWidth = requestedWidth == Target.SIZE_ORIGINAL ? sourceWidth : requestedWidth;
    int targetHeight = requestedHeight == Target.SIZE_ORIGINAL ? sourceHeight : requestedHeight;

    ImageType imageType = ImageHeaderParserUtils.getType(parsers, is, byteArrayPool);

    calculateScaling(/*各种参数*/);
    calculateConfig(/*各种参数*/);

    boolean isKitKatOrGreater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    if ((options.inSampleSize == 1 || isKitKatOrGreater) && shouldUsePool(imageType)) {
      int expectedWidth;
      int expectedHeight;
      if (sourceWidth >= 0 && sourceHeight >= 0 && fixBitmapToRequestedDimensions && isKitKatOrGreater) {
        expectedWidth = targetWidth;
        expectedHeight = targetHeight;
      } else {
        float densityMultiplier = isScaling(options) ? (float) options.inTargetDensity / options.inDensity : 1f;
        int sampleSize = options.inSampleSize;
        int downsampledWidth = (int) Math.ceil(sourceWidth / (float) sampleSize);
        int downsampledHeight = (int) Math.ceil(sourceHeight / (float) sampleSize);
        expectedWidth = Math.round(downsampledWidth * densityMultiplier);
        expectedHeight = Math.round(downsampledHeight * densityMultiplier);
      }
      if (expectedWidth > 0 && expectedHeight > 0) {
        setInBitmap(options, bitmapPool, expectedWidth, expectedHeight);
      }
    }
    Bitmap downsampled = decodeStream(is, options, callbacks, bitmapPool);
    callbacks.onDecodeComplete(bitmapPool, downsampled);

    Bitmap rotated = null;
    if (downsampled != null) {
      downsampled.setDensity(displayMetrics.densityDpi);
      rotated = TransformationUtils.rotateImageExif(bitmapPool, downsampled, orientation);
      if (!downsampled.equals(rotated)) {
        bitmapPool.put(downsampled);
      }
    }

    return rotated;
  }

##### Downsampler#decodeStream()

  private static Bitmap decodeStream(InputStream is, BitmapFactory.Options options,
      DecodeCallbacks callbacks, BitmapPool bitmapPool) throws IOException {
    if (options.inJustDecodeBounds) {
      is.mark(MARK_POSITION);
    } else {
      callbacks.onObtainBounds();
    }
    int sourceWidth = options.outWidth;
    int sourceHeight = options.outHeight;
    String outMimeType = options.outMimeType;
    final Bitmap result;
    TransformationUtils.getBitmapDrawableLock().lock();
    try {
      result = BitmapFactory.decodeStream(is, null, options);
    } catch (IllegalArgumentException e) {
      IOException bitmapAssertionException =
          newIoExceptionForInBitmapAssertion(e, sourceWidth, sourceHeight, outMimeType, options);
      if (options.inBitmap != null) {
        try {
          is.reset();
          bitmapPool.put(options.inBitmap);
          options.inBitmap = null;
          return decodeStream(is, options, callbacks, bitmapPool);
        } catch (IOException resetException) {
          throw bitmapAssertionException;
        }
      }
      throw bitmapAssertionException;
    } finally {
      TransformationUtils.getBitmapDrawableLock().unlock();
    }

    if (options.inJustDecodeBounds) {
      is.reset();
    }
    return result;
  }



### 得到了图片之后的逻辑：

当拿到了 Bitmap 之后又会一路 return 回到之前的位置：我们从下面的代码开始

##### DecodePath#decode()

  public Resource<Transcode> decode(DataRewinder<DataType> rewinder, int width, int height,
      Options options, DecodeCallback<ResourceType> callback) throws GlideException {
    Resource<ResourceType> decoded = decodeResource(rewinder, width, height, options);
    Resource<ResourceType> transformed = callback.onResourceDecoded(decoded);
    return transcoder.transcode(transformed, options);
  }

transcoder 是 ResourceTranscoder 的实现，用来将 Bitmap 转换成指定的类型。

##### DecodeJob 的内部类 DecodeCallback

   这里会调用 callback 进行回调，我们上面提到过它，

  private final class DecodeCallback<Z> implements DecodePath.DecodeCallback<Z> {

    private final DataSource dataSource;

    @Synthetic
    DecodeCallback(DataSource dataSource) {
      this.dataSource = dataSource;
    }

    @NonNull
    @Override
    public Resource<Z> onResourceDecoded(@NonNull Resource<Z> decoded) {
      return DecodeJob.this.onResourceDecoded(dataSource, decoded);
    }
  }

##### DecodeJob#onResourceDecoded()

  <Z> Resource<Z> onResourceDecoded(DataSource dataSource, Resource<Z> decoded) {
    Class<Z> resourceSubClass = (Class<Z>) decoded.get().getClass();
    Transformation<Z> appliedTransformation = null;
    Resource<Z> transformed = decoded;
    if (dataSource != DataSource.RESOURCE_DISK_CACHE) {
      appliedTransformation = decodeHelper.getTransformation(resourceSubClass);
      transformed = appliedTransformation.transform(glideContext, decoded, width, height);
    }
    if (!decoded.equals(transformed)) {
      decoded.recycle();
    }

    final EncodeStrategy encodeStrategy;
    final ResourceEncoder<Z> encoder;
    if (decodeHelper.isResourceEncoderAvailable(transformed)) {
      encoder = decodeHelper.getResultEncoder(transformed);
      encodeStrategy = encoder.getEncodeStrategy(options);
    } else {
      encoder = null;
      encodeStrategy = EncodeStrategy.NONE;
    }

    Resource<Z> result = transformed;
    boolean isFromAlternateCacheKey = !decodeHelper.isSourceKey(currentSourceKey);
    if (diskCacheStrategy.isResourceCacheable(isFromAlternateCacheKey, dataSource, encodeStrategy)) {
      if (encoder == null) {
        throw new Registry.NoResultEncoderAvailableException(transformed.get().getClass());
      }
      final Key key;
      switch (encodeStrategy) {
        case SOURCE:
          key = new DataCacheKey(currentSourceKey, signature);
          break;
        case TRANSFORMED:
          key = new ResourceCacheKey(
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
      deferredEncodeManager.init(key, encoder, lockedResult);
      result = lockedResult;
    }
    return result;
  }


### 转码之后

会一路回到下面的方法：

  private void notifyEncodeAndRelease(Resource<R> resource, DataSource dataSource) {
    if (resource instanceof Initializable) {
      ((Initializable) resource).initialize();
    }

    Resource<R> result = resource;
    LockedResource<R> lockedResource = null;
    if (deferredEncodeManager.hasResourceToEncode()) {
      lockedResource = LockedResource.obtain(resource);
      result = lockedResource;
    }

    notifyComplete(result, dataSource);

    stage = Stage.ENCODE;
    try {
      if (deferredEncodeManager.hasResourceToEncode()) {
        deferredEncodeManager.encode(diskCacheProvider, options);
      }
    } finally {
      if (lockedResource != null) {
        lockedResource.unlock();
      }
    }
    onEncodeComplete();
  }

##### EngineJob#handleResultOnMainThread()

然后从 notifyComplete() 进入到发送消息到主线程：

  void handleResultOnMainThread() {
    stateVerifier.throwIfRecycled();
    if (isCancelled) {
      resource.recycle();
      release(false /*isRemovedFromQueue*/);
      return;
    } else if (cbs.isEmpty()) {
      throw new IllegalStateException("Received a resource without any callbacks to notify");
    } else if (hasResource) {
      throw new IllegalStateException("Already have resource");
    }
    engineResource = engineResourceFactory.build(resource, isCacheable);
    hasResource = true;

    engineResource.acquire();
    listener.onEngineJobComplete(this, key, engineResource);

    for (int i = 0, size = cbs.size(); i < size; i++) {
      ResourceCallback cb = cbs.get(i);
      if (!isInIgnoredCallbacks(cb)) {
        engineResource.acquire();
        cb.onResourceReady(engineResource, dataSource);
      }
    }
    engineResource.release();

    release(false /*isRemovedFromQueue*/);
  }




