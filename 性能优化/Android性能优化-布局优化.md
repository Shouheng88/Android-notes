# Android 性能优化 - 布局优化

### 1. 合理选择 ViewGroup

在选择使用 Android 中的布局方式的时候应该遵循：**尽量少使用性能比较低的容器控件,比如 RelativeLayout，但如果使用 RelativeLayout 可以降低布局的层次的时候可以考虑使用**。

Android 中的控件是树状的，降低树的高度可以提升布局性能。RelativeLayout 的布局比 FrameLayout、LinearLayout 等简单，因而可以减少计算过程，提升程序性能。

*注：参见第 9 条，关于 ConstaintLayout 的介绍。*

### 2. 使用 `<include>` 标签复用布局

**多个地方共用的布局可以使用 `<include>` 标签在各个布局中复用**

```xml
   <include android:id="@+id/bar_layout" layout="@layout/layout_toolbar"/>
```

### 3. 使用 `<merge>` 标签复用父容器

**可以通过使用 `<merge>` 来降低布局的层次**。 `<merge>` 标签通常与 `<include>` 标签一起使用， `<merge>` 作为可以复用的布局的根控件。然后使用 `<include>` 标签引用该布局。

```xml
    <!--布局1-->
    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <include android:id="@+id/bar_layout" layout="@layout/layout_toolbar"/>
    </RelativeLayout>
    <!--布局2-->
    <merge>
        <Button
            android:layout_width="match_parent"
            android:layout_height="match_parent">
    </merge>
```

上面的布局方式中布局 2 被布局 1 引用之后其 Button 的控件父容器将会是布局 1 中的 RelativeLayout. 如果我们不使用 `<merge>` 标签而是一个单独的父容器的话就会多一层布局。

### 4. 使用 `<ViewStub>` 标签动态加载布局

**`<ViewStub>` 标签可以用来在程序运行的时候决定加载哪个布局，而不是一次性全部加载**。

```xml
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content">
        <ViewStub
            android:id="@+id/stub"
            android:inflatedId="@+id/inflatedStart"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout="@layout/start" />
    </LinearLayout>
```

然后我们可以使用下面两种方式将布局加载进来：

```java
((ViewStub) findViewById(R.id.stub)).setVisibility(View.VISIBILITY); // 方式 1
View v = ((ViewStub) findViewById(R.id.stub)).inflate(); // 方式 2
```

### 5. 性能分析：使用 Android Lint 来分析布局

在 AS 的 `Analyze->Inspect Code` 中配置检测的范围，然后开启检测，Android Lint 会对布局进行分析，然后对存在的问题，给出建议

可以在 `Settings->Editor->Inspections` 中配置 Android Lint.

Android Lint 不仅可以检测布局，还可以检测 Java 代码。

### 6. 性能分析：避免过度绘制

在手机的开发者选项中的**绘图**选项中选择**显示布局边界**来查看布局

蓝色、淡绿、淡红，深红代表了4种不同程度的 Overdraw 的情况，我们的目标就是尽量减少红色 Overdraw，看到更多的蓝色区域。

### 7. 性能分析：Hierarchy View

可以通过 Hierarchy View 来获取当前的 View 的层次图

在最新的 AS 中已经移除了，可以到 sdk 的 tools 目录下，打开 `monitor.bat`，然后工具来中的 DDMS 旁边的 `Open Perspective` 中打开 Hierarchy View

资料：

- [官方文档中关于 Hierarchy Viewer 的介绍](http://developer.android.com/tools/debugging/debugging-ui.html)

### 8. 使用 ConstaintLayout　

ConstaintLayout 是 Google 在 2016 年的 I/O 大会上发布的一个新的布局方式。可以通过阅读下面的资料来学习它的使用：

- [官方文档中关于 ConstraintLayout 的介绍](https://developer.android.google.cn/reference/android/support/constraint/ConstraintLayout)
- [Android新特性介绍，ConstraintLayout完全解析 - 郭霖](https://blog.csdn.net/guolin_blog/article/details/53122387)

这种布局方式的好处是，布局更加灵活，它在性能上面的贡献是，使用它可以降低布局的层次。

### 9. 性能分析：使用 systrace 分析 UI 性能

使用 Systrace 你需要：

1. 下载最新的 Android SDK Tools；
2. 安装 Python 环境；
3. 连接 API 18 以上的设备。

Systrace 的目录位于 `sdk/platform-tools/systrace/`

Systrace 的命令的格式：

```python
python systrace.py [options] [categories]
```

你可以通过阅读官方文档来了解它的各个选项和参数的意义。

资料：

- [官方文档中关于 Systrace 的介绍](https://developer.android.com/studio/command-line/systrace)



