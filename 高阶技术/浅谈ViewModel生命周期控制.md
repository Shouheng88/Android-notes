# 浅谈 ViewModel 的生命周期控制

## 1、从一个 Bug 说起

想必有过一定开发经验的同学对 ViewModel 都不会陌生，它是 Google 推出的 MVVM 架构模式的一部分。这里它的基础使用我们就不介绍了，毕竟这种类型的文章也遍地都是。今天我们着重来探讨一下它的生命周期。

起因是这样的，昨天在修复程序中的 Bug 的时候遇到了一个异常，是从 ViewModel 中获取存储的数据的时候报了空指针。我启用了开发者模式的 “不保留活动” 之后很容易地重现了这个异常。出现错误的原因也很简单，相关的代码如下：

```java
    private ReceiptViewerViewModel viewModel;

    @Override
    protected void doCreateView(Bundle savedInstanceState) {
        viewModel = ViewModelProviders.of(this).get(ReceiptViewerViewModel.class); // 1
        handleIntent(savedInstanceState);
        // ... 
    }

    private void handleIntent(Bundle savedInstanceState) {
        LoadingStatus loadingStatus;
        if (savedInstanceState == null) {
            loadingStatus = (LoadingStatus) getIntent().getSerializableExtra(Router.RECEIPT_VIEWER_LOADING_STATUS);
        }
        viewModel.setLoadingStatus(loadingStatus);
    }
```

在方法 `doCreateView()` 中我获取了 `viewModel` 实例，然后在 `handleIntent()` 方法中从 `Intent` 中取出传入的参数。当然，还要使用 `viewModel` 的 `getter` 方法从其中取出 `loadingStatus` 并使用。在使用的时候抛了空指针。

显然，一般情况下是不会出现问题的，但是如果 Activity 在后台被销毁了，那么再重建的时候就会出现空指针异常。

解决方法也比较简单，在 `onSaveInstanceState()` 方法中将数据缓存起来即可，即：

```java
    private void handleIntent(Bundle savedInstanceState) {
        LoadingStatus loadingStatus;
        if (savedInstanceState == null) {
            loadingStatus = (LoadingStatus) getIntent().getSerializableExtra(Router.RECEIPT_VIEWER_LOADING_STATUS);
        } else {
            loadingStatus = (LoadingStatus) savedInstanceState.get(Router.RECEIPT_VIEWER_LOADING_STATUS);
        }
        viewModel.setLoadingStatus(loadingStatus);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(Router.RECEIPT_VIEWER_LOADING_STATUS, viewModel.getLoadingStatus());
    }
```

现在的问题是 ViewModel 的生命周期问题，有人说在 `doCreateView()` 方法的 1 处得到的不是之前的 ViewModel 吗，数据不是之前已经设置过了吗？所以，这牵扯 ViewModel 是在什么时候被销毁和重建的问题。

## 2、ViewModel 的生命周期

有的人希望使用 ViewModel 缓存 Activity 的信息，然后在 `doCreateView()` 方法的 1 处得到之前的 ViewModel 实例，这样 ViewModel 的数据就是 Activity 销毁之前的数据，这可行吗？我们从源码角度来看下这个问题。

首先，每次获取 `viewmodel` 实例的时候都会调用下面的方法来获取 ViewModel 实例。从下面的 `get()` 方法中可以看出，实例化过的 ViewModel 是从 `mViewModelStore` 中获取的。如果由 `ViewModelStores.of(activity)` 方法得到的 `mViewModelStore` 不是同一个，那么得到的 ViewModel 也不是同一个。

下面方法中的 `get()` 方法中后续的逻辑是如果之前没有缓存过 ViewModel，那么就构建一个新的实例并将其放进 `mViewModelStore` 中。这部分代码逻辑比较简单，我们不继续分析了。

```java
    // ViewModelProviders#of()
    public static ViewModelProvider of(@NonNull FragmentActivity activity) {
        ViewModelProvider.AndroidViewModelFactory factory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(activity);
        return new ViewModelProvider(ViewModelStores.of(activity), factory); // 1
    }

    // ViewModelProvider#get()
    public <T extends ViewModel> T get(@NonNull String key, @NonNull Class<T> modelClass) {
        ViewModel viewModel = mViewModelStore.get(key);

        if (modelClass.isInstance(viewModel)) {
            return (T) viewModel;
        }

        viewModel = mFactory.create(modelClass);
        mViewModelStore.put(key, viewModel);
        return (T) viewModel;
    }
```

我们回到上述 `of()` 方法的 1 处，来看下 `ViewModelStores.of()` 方法，其定义如下：

```java
    // ViewModelStores#of()
    public static ViewModelStore of(@NonNull FragmentActivity activity) {
        if (activity instanceof ViewModelStoreOwner) {
            return ((ViewModelStoreOwner) activity).getViewModelStore();
        }
        return holderFragmentFor(activity).getViewModelStore();
    }

    // HolderFragment#holderFragmentFor()
    public static HolderFragment holderFragmentFor(FragmentActivity activity) {
        return sHolderFragmentManager.holderFragmentFor(activity);
    }
```

这里会从 `holderFragmentFor()` 方法中获取一个 `HolderFragment` 实例，它是一个 Fragment 的实现类。然后从该实例中获取 `ViewModelStore` 的实例。所以，ViewModel 对生命周期的管理与 Glide 和 RxPermission 等框架的处理方式一致，就是使用一个空的 Fragment 来进行生命周期管理。

对于 `HolderFragment`，其定义如下。从下面的代码我们可以看出，上述用到的 ViewModelStore 实例就是 `HolderFragment` 的一个局部变量。所以，ViewModel 使用空的 Fragment 管理生命周期实锤了。

```java
    public class HolderFragment extends Fragment implements ViewModelStoreOwner {
        private static final HolderFragmentManager sHolderFragmentManager = new HolderFragmentManager();
        private ViewModelStore mViewModelStore = new ViewModelStore();

        public HolderFragment() {
            setRetainInstance(true);
        }

        // ...
    }
```

此外，我们注意到上面的 HolderFragment 的构造方法中还调用了 `setRetainInstance(true)` 这一行代码。我们进入该方法看它的注释：

> Control whether a fragment instance is retained across Activity
> re-creation (such as from a configuration change).  This can only
> be used with fragments not in the back stack.  If set, the fragment
> lifecycle will be slightly different when an activity is recreated:

就是说，当 Activity 被重建的时候该 Fragment 会被保留，然后传递给新创建的 Activity. 但是，这只适用于不处于后台的 Fragment. 所以，如果 Activity 处于后台的时候，Fragment 不会保留，那么它得到的 `ViewModelStore` 实例就不同了。

所以，总结下来，准确地将：**当 Activity 处于前台的时候被销毁了，那么得到的 ViewModel 是之前实例过的 ViewModel；如果 Activity 处于后台时被销毁了，那么得到的 ViewModel 不是同一个。举例说，如果 Activity 因为配置发生变化而被重建了，那么当重建的时候，ViewModel 是之前的实例；如果因为长期处于后台而被销毁了，那么重建的时候，ViewModel 就不是之前的实例了。**

回到之前的 `holderFragmentFor()` 方法，我们看下这里具体做了什么，其定义如下。

```java
    // HolderFragmentManager#holderFragmentFor()
    HolderFragment holderFragmentFor(FragmentActivity activity) {
        // 使用 FragmentManager 获取 HolderFragment
        FragmentManager fm = activity.getSupportFragmentManager();
        HolderFragment holder = findHolderFragment(fm);
        if (holder != null) {
            return holder;
        }
        // 从哈希表中获取 HolderFragment
        holder = mNotCommittedActivityHolders.get(activity);
        if (holder != null) {
            return holder;
        }

        if (!mActivityCallbacksIsAdded) {
            mActivityCallbacksIsAdded = true;
            activity.getApplication().registerActivityLifecycleCallbacks(mActivityCallbacks);
        }
        holder = createHolderFragment(fm);
        // 将新的实例放进哈希表中
        mNotCommittedActivityHolders.put(activity, holder);
        return holder;
    }
```

首先，尝试使用 `FragmentManager` 来获取 `HolderFragment`，如果获取不到就从 `mNotCommittedActivityHolders` 中进行获取。这里的 `mNotCommittedActivityHolders` 是一个哈希表，每次实例化的新的 HolderFragment 会被添加到哈希表中。

另外，上面的方法中还使用了 ActivityLifecycleCallbacks 对 Activity 的生命周期进行监听。其定义如下，

```java
    private ActivityLifecycleCallbacks mActivityCallbacks =
            new EmptyActivityLifecycleCallbacks() {
                @Override
                public void onActivityDestroyed(Activity activity) {
                    HolderFragment fragment = mNotCommittedActivityHolders.remove(activity);
                }
            };
```

当 Activity 被销毁的时候会从哈希表中移除映射关系。所以，每次 Activity 被销毁的时候哈希表中的映射关系都不存在了。而之所以 ViewModel 能够实现在 Activity 配置发生变化的时候获取之前的 ViewModel 是通过上面的 `setRetainInstance(true)` 和 `findHolderFragment(fm)` 来实现的。

## 总结

以上就是 ViewModel 的生命周期的总结。我们只是通过对主流程的分析研究了它的生命周期的流程，实际上内部还有许多小细节，逻辑也比较简单，我们就不一一说明了。

其实，从 Google 的官方文档中，我们也能够得到上面的总结，

![viewmodel-lifecycle](res/viewmodel-lifecycle.png)

这里使用了 `Activity rotated`，也就是 Activity 处于前台的时候配置发生变化的情况，而不是处于后台，不知道你之前有没有注意这一点呢？

以上。

