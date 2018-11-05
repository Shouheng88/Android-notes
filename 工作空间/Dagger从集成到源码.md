# Dagger新手指南：从集成到源码带你理解依赖注入框架

> 本文从例子到源码来帮助你学习和理解Dagger的集成，因为只有例子没有源码的博文看了之后经常让人一头雾水。
> 学编程重点在于理解，而不是死记硬背每个注解该怎么使用！
> 所以，本文先用一个例子介绍Dagger的基本集成方式，然后，我们再看一下每个点具体的源码是如何实现的。

## 啥是依赖注入？

依赖注入就是取代了我们常用的setter和getter方法，也就是你不用每次调用某个示例的方法为它的一个变量赋值，
你可以使用依赖注入直接将值注入进去，也就是使用依赖注入为实例的变量赋值。

依赖注入在服务端比较常见，经典的如Spring。而Dagger是一个小型的依赖注入框架，毕竟运行在移动端的代码要考虑程序的体积之类的。

简单了解了依赖注入的概念，我们看下Dagger的基本使用方法和它的源码。
其实，Activity和Service的实现逻辑大同小异，我们没有必要面面俱到，所以，这里我们只以Activity的集成和源码为例。

## 以Activity的集成为例

我们以Activity的集成为例：首先我们自定义一个Application并将其配置到Manifest文件中：

```
  public class MyApplication extends Application implements HasActivityInjector {

    @Inject DispatchingAndroidInjector<Activity> activityInjector;

    @Override
    public void onCreate() {
        super.onCreate();
        DaggerAppComponent.builder().application(this).build().inject(this);
    }

    @Override
    public AndroidInjector<Activity> activityInjector() {
        return activityInjector;
    }
  }
```

这里，我们让自定义的Application实现HasActivityInjector接口，该接口中只有一个`public AndroidInjector<Activity> activityInjector()`方法。
正如上文所示，我们实现了该接口，并将注入到Application中的`activityInjector`作为值返回。

先不管`activityInjector`是如何注入到Application中的，我们先看一下如何配置向Activity中进行注入。

```
public abstract class CommonDaggerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);
    }
}
```

我们定义一个名为CommonDaggerActivity的抽象类，并在它的`onCreate`方法中使用`AndroidInjection.inject(this);`进行注入。
我们进入看它的源码，简化一下，移除没有用的代码之后：

```
  public static void inject(Activity activity) {
    Application application = activity.getApplication();
    if (!(application instanceof HasActivityInjector)) { throw new RuntimeException(...) }
    AndroidInjector<Activity> activityInjector = ((HasActivityInjector) application).activityInjector();
    activityInjector.inject(activity);
  }
```

所以，本质上就是获取当前Activity对应的Application，然后将该Application向下转型为HasActivityInjector。
因为我们的Application是实现了HasActivityInjector接口的，所以可以成功向下转型，并获取到AndroidInjector<Activity>。
在获取了AndroidInjector<Activity>之后，并将当前的Activity注入进去。

那么，现在我们有了一些思路了。不过还有几个问题：

1. Application中的`activityInjector`是如何被注入进去的，以及它是如何被初始化的？
2. 当在Activity中调用了`AndroidInjection.inject(this)`之后发生了什么？

## 更完整的示例

你可能已经注意到，实际上在`MyApplication`中还有下面一行代码：

```
DaggerAppComponent.builder().application(this).build().inject(this)
```

我们的DaggerAppComponent是由AppComponent在编译时自动生成的。（你可以在代码编译之后，从`Android`切换到`Project`来查看生成的代码。）

这里的`AppComponent`的定义如下：

```
@Singleton
@Component(modules = {ActivityModule.class, ViewModelModule.class})
public interface AppComponent extends AndroidInjector<MyApplication> {

    @Component.Builder
    interface Builder {
        @BindsInstance Builder application(Application application);
        AppComponent build();
    }
}
```

我们用`@Component`注解，该注解中还定义了`@Builder`注解，正如你从上面的代码看到的。
你可以对照这个代码和生成的DaggerAppComponent，你会发现其实这里使用的是构建者模式。就是说：

**使用@Component注解定义的类会生成DaggerComponent，使用@Component.Builder注解定义的内部类会作为构建器来使用。你可以通过在@Component.Builder注解的接口中按照需要添加自己的方法，**

然后，这里在注解`@Component`中还通过modules引用了`ActivityModule.class`和`ViewModelModule.class`。
它们是定义的模块，我们在其中声明自己需要使用的变量等。下面给出它们的定义：

```
@Module
public abstract class ActivityModule {

    @ActivityScoped
    @ContributesAndroidInjector
    abstract MainActivity mainActivity();
}

@Module
public abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel.class)
    abstract ViewModel bindMainViewModel(MainViewModel mainViewModel);
}
```

这里用到了两个自定义注解：

```
@Documented
@Scope
@Retention(RetentionPolicy.RUNTIME)
public @interface ActivityScoped { }

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@MapKey
public @interface ViewModelKey {
    Class<? extends ViewModel> value();
}
```

这里的`MainViewModel`是我们定义的ViewModel，用来演示注入到Activity中之后发生了什么。

```
public class MainViewModel  extends AndroidViewModel {
    private static final String TAG = "MainViewModel";

    @Inject
    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public void log() {
        Log.d(TAG, "log: ");
    }
}
```

这里还有个MainActivity，以下是它的定义：

```
public class MainActivity extends CommonDaggerActivity {

    @Inject
    public MainViewModel mainViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainViewModel.log();
    }
}
```

显然，这里我们希望通过注入来为MainActivity的局部变量`mainViewModel`赋值，并在`onCreate()`方法调用它的方法。

当我们编译并执行程序之后，一切跟我们预期的一样：MainActivity被执行，MainActivity被注入进去，并成功输出了日志。

## 点击build之后发生了什么

上面我们通过一些简单的分析，知道了在Activity中调用`AndroidInjection.inject(this)`，实际上调用了`MyApplication`的`activityInjector`的`inject(activity)`方法。
然后，将MainActivity的字段注入进去。不过，我们还存在一些疑问，现在我们就对这些问题进行解答。

尝试在AS中先执行clean然后再执行build，到build下面去看下，生成了一些代码，其中就包含了DaggerComponent，似乎一切的魔力就发生在这几秒钟的时间里。

我们已经知道了在Activity中调用`AndroidInjection.inject(this)`，实际上调用了`MyApplication`的`activityInjector`的`inject(activity)`方法。
还需要知道`MyApplication`中的`activityInjector`是如何被创建并注入的。

从DaggerComponent那里作为分析的起点，当调用了`inject(MyApplication)`之后最终调用了：

```
MyApplication_MembersInjector.injectActivityInjector(instance, getDispatchingAndroidInjectorOfActivity());
```

而`activityInjector`就是从这里传入并初始化的。所以，`activityInjector`的创建是在`getDispatchingAndroidInjectorOfActivity()`中完成的。

果然，这里的`getDispatchingAndroidInjectorOfActivity()`通过下面的代码创建`activityInjector`并将其返回：

```
DispatchingAndroidInjector_Factory.newDispatchingAndroidInjector(getMapOfClassOfAndProviderOfFactoryOf())
```

而这里的`getMapOfClassOfAndProviderOfFactoryOf()`方法返回的是一个映射表，将我们配置的Activity通过字典与Provider关联起来。

那`newDispatchingAndroidInjector()`方法又做了什么呢？它使用上述字典作为参数，`new`一个`DispatchingAndroidInjector`实例。

好了，整理一下：实际上，当我们调用`AndroidInjection.inject(this)`的时候，调用了`new`出的`DispatchingAndroidInjector`实例的`inject(Activity)`方法。

那么，我们再来看一下`DispatchingAndroidInjector`中的`inject(Activity)`方法做了什么：

```
  public void inject(T instance) {
    // 实际调用inject方法的时候会调用maybeInject方法
    boolean wasInjected = maybeInject(instance);
    if (!wasInjected) {
      throw new IllegalArgumentException(errorMessageSuggestions(instance));
    }
  }

  public boolean maybeInject(T instance) {
    // 这里先从我们上述的字典中取出Provider
    Provider<AndroidInjector.Factory<? extends T>> factoryProvider = injectorFactories.get(instance.getClass());
    if (factoryProvider == null) {
      return false;
    }

	// 然后从Provider中取出AndroidInjector.Factory方法
    AndroidInjector.Factory<T> factory = (AndroidInjector.Factory<T>) factoryProvider.get();
    try {
	  // 最后调用AndroidInjector.Factory的create()方法，获取一个“注入器”
      AndroidInjector<T> injector = factory.create(instance);
	  // 调用"注入器"进行注入
      injector.inject(instance);
      return true;
    } catch (ClassCastException e) {
      throw new InvalidInjectorBindingException(...);
    }
  }
```

对应上面的代码分析：

首先获取被传入对象的Class，并从字典中获取Provider，这里是使用`MainActivity.class`获取到`mainActivitySubcomponentBuilderProvider`。
`mainActivitySubcomponentBuilderProvider`是在DaggerComponent中创建的，我们可以到DaggerComponent中看它的逻辑。

调用Provider的`get`方法将创建并返回一个`MainActivitySubcomponentBuilder`实例（`MainActivitySubcomponentBuilder`最终的继承自`AndroidInjector.Factory<T>`）。

然后，我们调用了`MainActivitySubcomponentBuilder`的`create()`方法，会先执行了`seedInstance(instance)`，然后执行了`build()`创建并返回一个“注入器”。
最后，就是使用该"注入器"的`inject()`方法向MainActivity中的字段赋值的。

这里的`seedInstance(instance)`和`build()`是两个模板方法，它们在`MainActivitySubcomponentBuilder`中实现并返回"注入器"。
而"注入器“实际上是`MainActivitySubcomponentImpl`的一个实例。那也就是说，实际上是使用了`MainActivitySubcomponentImpl`的`inject()`方法完成值的注入的。

我们看下这个`inject()`方法的定义，它最终会执行下面这串代码：

```
MainActivity_MembersInjector.injectMainViewModel(instance, getMainViewModel());
```

这里的`getMainViewModel()`方法也定义在`MainActivitySubcomponentImpl`中：

```
    private MainViewModel getMainViewModel() {
      return new MainViewModel(DaggerAppComponent.this.application);
    }
```

可以看出，它的定义方式与我们定义的构造方法一致。

最后的最后，我们会执行MainActivity_MembersInjector的方法完成注入：

```
  public static void injectMainViewModel(MainActivity instance, MainViewModel mainViewModel) {
    instance.mainViewModel = mainViewModel;
  }
```

## 总结

在上文中，我们通过分析生成的代码和我们的源码对Dagger的注入的原理进行了简单分析. 当然,在这里我们并没有深入去分析Dagger的框架的实现原理. 
因为这些生成的代码命名非常不规范,所以也导致我们分析的过程不那么简洁.

我们做简单的总结如下:
1. `@Component`使用了构建者模式,我们可以对构建的过程需要的字段进行自定义;
2. 需要注入变量的类会在编译期间生成一个名为`类名_MembersInjector`的注入器,并在使用名为`inject变量名`的静态方法进行变量注入;

我们已经分析了Dagger的作用的原理, 相信通过这些简单的分析, 你至少已经不会对Dagger那么陌生了, 以后我们有机会会更多的分析它的实现的原理. 
