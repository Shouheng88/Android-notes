# View 体系详解：View 的工作流程

## 1、View 树的加载流程

当我们调用 `startActivity()` 方法的时候，会调用到 `ActivityThread` 中的 `performLaunchActivity()` 获取一个 Activity 实例， 并在 `Instrumentation` 的 `callActivityOnCreate()`  方法中调用 Activity 的 `onCreate()` 完成 DecorView 的创建。这样我们就获取了一个 Activity 的实例，然后我们调用 `handleResumeActivity()` 来回调 Activity 的 `onResume()`：

```java
    private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent, String reason) {
        // ....
        WindowManagerGlobal.initialize();
        // 创建 Activity 的实例，在这里完成对 Activity 的 onCreate() 方法的回调
        Activity a = performLaunchActivity(r, customIntent);
        if (a != null) {
            // ...
            // 在这里回调 Activity 的 onResume() 方法
            handleResumeActivity(r.token, false, r.isForward,
                    !r.activity.mFinished && !r.startsNotResumed, r.lastProcessedSeq, reason);
            if (!r.activity.mFinished && r.startsNotResumed) {
                // 在这里完成对 Activity 的 onPause() 方法的回调
                performPauseActivityIfNeeded(r, reason);
                // ...
            }
        }
        // ...
    }
```

然后，在 `handleResumeActivity()` 方法中的 `performResumeActivity()` 会回调 Activity 的 `onResume()` 方法。在该方法中，我们会从 Window 中获取之前添加进去的 DecorView，然后将其添加到 WindowManager 中：

```java
    final void handleResumeActivity(IBinder token, boolean clearHide, boolean isForward, boolean reallyResume, int seq, String reason) {
        // 在这里会回调 Activity 的 onResume()
        r = performResumeActivity(token, clearHide, reason);
        if (r != null) {
            final Activity a = r.activity;
            // ...
            if (r.window == null && !a.mFinished && willBeVisible) {
                r.window = r.activity.getWindow();
                // 在这里获取 DecorView
                View decor = r.window.getDecorView();
                decor.setVisibility(View.INVISIBLE);
                // 获取 WindowManager 实例，实际是 WindowManagerImpl 
                ViewManager wm = a.getWindowManager();
                WindowManager.LayoutParams l = r.window.getAttributes();
                a.mDecor = decor;
                l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
                l.softInputMode |= forwardBit;
                if (r.mPreserveWindow) {
                    a.mWindowAdded = true;
                    r.mPreserveWindow = false;
                    // Activity 被重建，复用 DecorView，通知子元素
                    ViewRootImpl impl = decor.getViewRootImpl();
                    if (impl != null) {
                        impl.notifyChildRebuilt();
                    }
                }
                if (a.mVisibleFromClient) {
                    if (!a.mWindowAdded) {
                        a.mWindowAdded = true;
                        // 将 DecorView 添加到 WindowManager 中
                        wm.addView(decor, l);
                    } else {
                        a.onWindowAttributesChanged(l);
                    }
                }
            }
        }
    }
```

这里的 `WindowManager` 是 `WindowManagerImpl` 的实例，而调用它的 `addView()` 方法的时候会使用 `WindowManagerGlobal` 的 `addView()` 方法。在该方法中会 new 出来一个 `ViewRootImpl`，然后调用它的 `setView()` 把传进来的 `DecorView` 添加到 `Window` 里。同时，会调用 `requestLayout()` 方法进行布局，然后，并最终调用 `performTraversals()` 完成对整个 View 树进行遍历：

```java
    private void performTraversals() {
        // ...
        if (!mStopped || mReportNextDraw) {
            // ...
            performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
        // ...
        final boolean didLayout = layoutRequested && (!mStopped || mReportNextDraw);
        boolean triggerGlobalLayoutListener = didLayout
                || mAttachInfo.mRecomputeGlobalAttributes;
        if (didLayout) {
            performLayout(lp, mWidth, mHeight);
            // ...
        }
        // ...
        if (!cancelDraw && !newSurface) {
            // ...
            performDraw();
        }
    }
```

在该方法中会调用 `performMeasure()`、`performLayout()` 和 `performDraw()` 三个方法，它们分别会调用 DecorView 的 `measure()`、`layout()` 和 `draw()` 完成对**整个 View 树的测量、布局和绘制**，一个界面也就呈现给用户了。如果您做过自定义 View 的话，那么您对 `onMeasure()`、`onLayout()` 和 `onDraw()`三个方法一定不会陌生，前面的三个方法与后面的三个方法之间的关系就是：后面的三个方法会被前面的三个方法调用，本质上就是提供给用户用来自定义的方法。下面我们就看下这三个方法究竟各自做了什么操作，当然，我们尽可能从自定义控件的角度来分析，因为这对一个开发者可能帮助更大。

## 2、measure()

View 的大小不仅由自身所决定，同时也会受到父控件的影响，为了我们的控件能更好的适应各种情况，一般会自己进行测量。在上面我们提到了 `measure()` 方法，它是用来测量 View 的大小的，但实际上测量的主要工作是交给 `onMeasure()` 方法的。在 View 中，`onMeasure()` 是一个 `protected` 的方法，显然它设计的目的就是：提供给子 View 按照父容器提供的限制条件，控制自身的大小，实现自己大小的测量逻辑。所以，当我们自定义一个控件的时候，只会去覆写 `onMeasure()` 而不去覆写 `measure()` 方法。

在 Android 中，我们的控件分成 View 和 ViewGroup 两种类型。根据上面的分析，对 View 的测量，我们可以得出如下结论：在 Android 中，ViewGroup 会根据其自身的布局特点，把限制条件封装成 `widthMeasureSpec` 和 `heightMeasureSpec` 两个参数传递给子元素；然后，在子元素中根据这两个参数来调整自身的大小。所以，ViewGroup 的 `measure()` 方法会根据其布局特性的不同而不同；而 View 的 `measure()`，不论其父容器是哪种类型，只根据 `widthMeasureSpec` 和 `heightMeasureSpec` 决定。

下面我们来看一下 `onMeasure()` 在 View 和 ViewGroup 中的不同表现形式。

### 2.1 View 的 onMeasure()

下面是 View 类中的 `onMeasure()` 方法。这是一个默认的实现，调用了 `setMeasuredDimension()` 方法来存储测量之后的宽度和高度。**当我们自定义 View 的时候，也需要调用 setMeasuredDimension() 方法把最终的测量结果存储起来**：

```java
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
            getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
            getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
```

显然，我们的测量依据就是 `widthMeasureSpec` 和 `heightMeasureSpec` 两个参数。它们是整型的、32位变量，包含了测量模式和测量数值的信息（按位存储到整型变量上，包装成整型的目的是为了节约存储空间）。一般我们会像下面这样来分别获取高度和宽度的测量模式和测量数值（实际就是按位截取）：

```java    
    int widthsize = MeasureSpec.getSize(widthMeasureSpec);      // 测量数值
    int widthmode = MeasureSpec.getMode(widthMeasureSpec);      // 测量模式    
    int heightsize = MeasureSpec.getSize(heightMeasureSpec);    // 测量数值
    int heightmode = MeasureSpec.getMode(heightMeasureSpec);    // 测量模式
```

测量模式共有 `MeasureSpec.UNSPECIFIED`、`MeasureSpec.AT_MOST` 和 `MeasureSpec.EXACTLY` 三种，分别对应二进制数值 `00`、`01` 和 `10`，它们各自的含义如下：

1. `UNSPECIFIED`：默认值，父控件没有给子 View 任何限制，子 View 可以设置为任意大小；
2. `EXACTLY`：表示父控件已经确切的指定了子 View 的大小；
3. `AT_MOST`：表示子 View 具体大小没有尺寸限制，但是存在上限，上限一般为父 View 大小。

这里，我不打算详细介绍 View 中默认测量逻辑的具体实现。它的大致逻辑是这样的：首先我们会用 `getDefaultSize()` 获取默认的宽度或者高度，这个方法接收两个参数，一个是默认的尺寸，一个测量模式。如果父控件没有给它任何限制，它就使用默认的尺寸，否则使用测量数值。这里的默认的尺寸通过 `getSuggestedMinimumHeight()`/`getSuggestedMinimumWidth()` 方法得到，它会根据背景图片高度/宽度和 `mMinHeight`/`mMinWidth` 的值，取一个最大的值作为控件的高度/宽度。

所以，View 的默认的测量逻辑的实际效果是：首先 View 的大小受父容器的影响，如果父容器没有给它限制的话，它会取背景图片和最小的高度或者宽度中取一个最大的值作为自己的大小。

### 2.2 ViewGroup 的 onMeasure()

#### 2.2.1 ViewGroup 中的方法

由于 ViewGroup 本身没有布局的特点，所以它没有覆写 `onMeasure()`。有自身布局特点的，比如 `LinearLayout` 和 `RelativeLayout` 等都覆写并实现了这个方法。尽管如此，ViewGroup 提供了一些方法帮助我们进行测量，首先是 `measureChildren()` 方法：

```java
    protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        final int size = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < size; ++i) {
            final View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
            }
        }
    }
```

这里的逻辑比较简单，就是对子元素进行遍历并判断如果指定的 View 是否位 `GONE` 的状态，如果不是就调用 `measureChild()` 方法：

```java
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        final LayoutParams lp = child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }
```

该方法也比较容易理解，就是将子元素的布局参数 `LayoutParams` 取出，获取它的宽度和高度之后，将所有信息传递给 `getChildMeasureSpec()`。这样就得到了用于子元素布局的 `childWidthMeasureSpec` 和 `childHeightMeasureSpec` 参数。然后，再调用子元素的 `measure()` 方法，从而依次完成对整个 View 树的遍历。下面我们看下 `getChildMeasureSpec()` 方法做了什么操作：

```java
    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        // 首先从 spec 中取出父控件的测量模式和测量数值
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        // 这里需要保证 size 不能为负数，也就是预留给子元素的最大空间，由父元素的测量数值减去填充得到
        int size = Math.max(0, specSize - padding);
        // 用于返回的值
        int resultSize = 0;
        int resultMode = 0;
        // 根据父空间的测量模式
        switch (specMode) {
            // 父控件的大小是固定的
            case MeasureSpec.EXACTLY:
                if (childDimension >= 0) {
                    // 子 View 指定了大小
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    // 子元素希望大小与父控件相同（填满整个父控件）
                    resultSize = size;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    // 子元素希望有自己决定大小，但是不能比父控件大
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;
            // 父控件的具体大小没有尺寸限制，但是存在上限
            case MeasureSpec.AT_MOST:
                if (childDimension >= 0) {
                    // 子 View 指定了大小
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    // 子控件希望与父控件大小一致，但是父控件的大小也是不确定的，故让子控件不要比父控件大
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    // 子控件希望自己决定大小，限制其不要比父控件大
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;
            // 父控件没有任何限制，可以设置为任意大小
            case MeasureSpec.UNSPECIFIED:
                if (childDimension >= 0) {
                    // 子元素设置了大小
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    // 子控件希望和父控件一样大，但是父控件多大都不确定；系统23以下返回true，以上返回size
                    resultSize = View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
                    resultMode = MeasureSpec.UNSPECIFIED;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
        }
        // 返回一个封装好的测量结果，就是把测量数值和测量模式封装成一个32位的整数
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }
```

上面我们已经为这段代码作了非常详细的注释。只需要注意，这里在获取子元素的测量结果的时候是基于父控件的测量结果来的，需要根据父元素的测量模式和测量数值结合自身的布局特点分成上面九种情况。或者可以按照下面的写法将其划分成下面几种情况：

```java
    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int specMode = MeasureSpec.getMode(spec), specSize = MeasureSpec.getSize(spec);
        int size = Math.max(0, specSize - padding);
        int resultSize = 0, resultMode = 0;
        if (childDimension >= 0) {
            // 子元素指定了具体的大小，就用子元素的大小
            resultSize = childDimension;
            resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == ViewGroup.LayoutParams.MATCH_PARENT) {
            // 子元素希望和父控件一样大，需要设置其上限，然后测量模式与父控件一致即可
            if (specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST) {
                resultSize = size;
                resultMode = specMode;
            } else if (specMode == MeasureSpec.UNSPECIFIED) {
                // API23一下就是0，父控件没有指定大小的时候，子控件只能是0；以上是size
                resultSize = View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
                resultMode = MeasureSpec.UNSPECIFIED;
            }
        } else if (childDimension == ViewGroup.LayoutParams.WRAP_CONTENT) {
            // 子元素希望自己决定大小，设置其大小的上限是父控件的大小即可
            if (specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST) {
                resultSize = size;
                resultMode = MeasureSpec.AT_MOST;
            } else if (specMode == MeasureSpec.UNSPECIFIED) {
                // API23一下就是0，父控件没有指定大小的时候，子控件只能是0；以上是size
                resultSize = View.sUseZeroUnspecifiedMeasureSpec ? 0 : size;
                resultMode = MeasureSpec.UNSPECIFIED;
            }
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }
```

这两种方式只是划分的角度不一样，后面的这种方法是从子元素的布局参数上面来考虑的。另外，这里有个 `sUseZeroUnspecifiedMeasureSpec` 布尔参数需要提及一下，会根据系统的版本来进行赋值：

```java
    sUseZeroUnspecifiedMeasureSpec = targetSdkVersion < Build.VERSION_CODES.M;
```

也就是当系统是 API23 以下的时候的为 `true`. 加入这个参数的原因是，API23 之后，当父控件的测量模式是 `UNSPECIFIED` 的时候，子元素可以给父控件提供一个可能的大小。下面是注释的原话 ;-)

```java
    // In M and newer, our widgets can pass a "hint" value in the size
    // for UNSPECIFIED MeasureSpecs. This lets child views of scrolling containers
    // know what the expected parent size is going to be, so e.g. list items can size
    // themselves at 1/3 the size of their container. It breaks older apps though,
    // specifically apps that use some popular open source libraries.
```

#### 2.2.2 LinearLayout 的 onMeasure()

上面我们分析的是 ViewGroup 中提供的一些方法，下面我们以 LinearLayout 为例，看一下一个标准的容器类型的控件是如何实现其测量的逻辑的。

下面是其 `onMeasure()` 方法，显然在进行测量的时候会根据其布局的方向分别实现测量的逻辑：

```java
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mOrientation == VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec);
        }
    }
```

然后，我们以 `measureVertical()` 为例，来看一下 LinearLayout 在垂直方向上面是如何进行测量的。这段代码比较长，我们只截取其中的一部分来进行分析：

```java
    void measureVertical(int widthMeasureSpec, int heightMeasureSpec) {
        // ...
        // 获取LinearLayout的测量模式
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        // ...
        mTotalLength += mPaddingTop + mPaddingBottom;
        int heightSize = mTotalLength;
        heightSize = Math.max(heightSize, getSuggestedMinimumHeight());
        // ...
            for (int i = 0; i < count; ++i) {
                final View child = getVirtualChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) {
                    continue;
                }
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final float childWeight = lp.weight;
                if (childWeight > 0) {
                    // ...
                    // 获取一个测量的数值和测量模式
                    final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            Math.max(0, childHeight), MeasureSpec.EXACTLY);
                    final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                            mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin,
                            lp.width);
                    // 调用子元素进行测量
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                    childState = combineMeasuredStates(childState, child.getMeasuredState()
                            & (MEASURED_STATE_MASK>>MEASURED_HEIGHT_STATE_SHIFT));
                }
                
                final int margin =  lp.leftMargin + lp.rightMargin;
                final int measuredWidth = child.getMeasuredWidth() + margin;
                maxWidth = Math.max(maxWidth, measuredWidth);

                boolean matchWidthLocally = widthMode != MeasureSpec.EXACTLY &&
                        lp.width == LayoutParams.MATCH_PARENT;

                alternativeMaxWidth = Math.max(alternativeMaxWidth,
                        matchWidthLocally ? margin : measuredWidth);

                allFillParent = allFillParent && lp.width == LayoutParams.MATCH_PARENT;

                final int totalLength = mTotalLength;
                // 将宽度增加到 mTotalLength 上
                mTotalLength = Math.max(totalLength, totalLength + child.getMeasuredHeight() +
                        lp.topMargin + lp.bottomMargin + getNextLocationOffset(child));
            }
            mTotalLength += mPaddingTop + mPaddingBottom;
        // ...
        maxWidth += mPaddingLeft + mPaddingRight;
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        // 最终确定测量的大小
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                heightSizeAndState);
        // ...
    }
```

上面是 LinearLayout 在垂直方向上面的测量的过程，在测量的时候会根据子元素的布局将子元素的测量高度添加到 `mTotalLength` 上，然后再加上填充的大小，作为最终的测量结果。

## 3、layout()

 `layout()` 用于确定控件的位置，它提供了 `onLayout()` 来交给字类实现，同样我们在自定义控件的时候只要实现 `onLayout()` 方法即可。在我们自定义 View 的时候，如果定义的是非 ViewGroup 类型的控件，一般是不需要覆写 `onLayout()` 方法的。

下面我们先看一下 `layout()` 方法在 View 中的实现：

```java
    public void layout(int l, int t, int r, int b) {
        if ((mPrivateFlags3 & PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT) != 0) {
            onMeasure(mOldWidthMeasureSpec, mOldHeightMeasureSpec);
            mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
        }

        int oldL = mLeft;
        int oldT = mTop;
        int oldB = mBottom;
        int oldR = mRight;

        boolean changed = isLayoutModeOptical(mParent) ?
                setOpticalFrame(l, t, r, b) : setFrame(l, t, r, b);

        if (changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {
            onLayout(changed, l, t, r, b);
            // ...
        }

        // ...
    }
```

这里会调用 `setFrame()` 方法，它的主要作用是根据新的布局参数和老的布局参数做一个对比，以判断控件的大小是否发生了变化，如果变化了的话就调用 `invalidate()` 方法并传入参数 `true`，以表明绘图的缓存也发生了变化。这里就不给出这个方法的具体实现了。然后注意到，在 `layout()` 方法中会回调 `onLayout()` 方法来完成各个控件的位置的确定。

对于 ViewGroup，它重写了 `layout()` 并在其中调用了 View 中的 `layout()` 方法，不过整体并没有做太多的逻辑。与测量过程类似，ViewGroup 并没有实现 `onLayout` 方法。同样，对于 ViewGroup 类型的控件，我们还是以 LinearLayout 为例说明一下 `onLayout()` 的实现逻辑：

与测量过程类似，LinearLayout 在 layout 的时候也根据布局的方向分成两种情形：

```java
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mOrientation == VERTICAL) {
            layoutVertical(l, t, r, b);
        } else {
            layoutHorizontal(l, t, r, b);
        }
    }
```

这里我们仍以垂直方向的方法为例。与测量的过程相比，layout 的过程的显得简单、清晰得多：

```java
    void layoutVertical(int left, int top, int right, int bottom) {
        // ...
        // 根据控件的 gravity 特点得到顶部的位置
        switch (majorGravity) {
           case Gravity.BOTTOM:
               childTop = mPaddingTop + bottom - top - mTotalLength;
               break;
           case Gravity.CENTER_VERTICAL:
               childTop = mPaddingTop + (bottom - top - mTotalLength) / 2;
               break;
           case Gravity.TOP:
           default:
               childTop = mPaddingTop;
               break;
        }

        // 遍历子控件
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                childTop += measureNullChild(i);
            } else if (child.getVisibility() != GONE) {
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                final LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) child.getLayoutParams();

                int gravity = lp.gravity;
                if (gravity < 0) {
                    gravity = minorGravity;
                }
                final int layoutDirection = getLayoutDirection();
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                // 得到子控件的左边的位置
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = paddingLeft + ((childSpace - childWidth) / 2)
                                + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = childRight - childWidth - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = paddingLeft + lp.leftMargin;
                        break;
                }

                if (hasDividerBeforeChildAt(i)) {
                    childTop += mDividerHeight;
                }

                childTop += lp.topMargin;
                // 本质上调用子控件的 layout() 方法
                setChildFrame(child, childLeft, childTop + getLocationOffset(child),
                        childWidth, childHeight);
                childTop += childHeight + lp.bottomMargin + getNextLocationOffset(child);
                i += getChildrenSkipCount(child, i);
            }
        }
    }
```

因为布局方向是垂直方向的，所以在对子元素进行遍历之前，先对自身的顶部的位置进行计算，然后再依次遍历子元素，并对顶部的高度不断叠加，最后调用 `setChildFrame()` 方法:

```java
    private void setChildFrame(View child, int left, int top, int width, int height) {
        child.layout(left, top, left + width, top + height);
    }
```

这样就完成了对整个 View 树的 `layout()` 方法的调用。

## 4、draw()

View 的 `draw()` 方法实现的逻辑也很清晰。在绘制的过程会按照如下的步骤进行：

1. 绘制背景
2. 保存 canvas
3. 绘制自身的内容
4. 绘制子控件
5. 绘制 View 的褪色边缘，比如阴影效果之类的
6. 绘制装饰，比如滚动条之类的

View 中提供了 `onDraw()` 方法用来完成对自身的内容的绘制，所以，我们自定义 View 的时候只要重写这个方法就可以了。当我们要自定义一个 ViewGroup 类型的控件的时候，一般是不需要重写 `onDraw()` 方法的，因为它只需要遍历子控件并依次调用它们的 `draw()` 方法就可以了。（当然，如果非要实现的话，也是可以的。）

下面是这部分代码，代码的注释中也详细注释了每个步骤的逻辑：

```java
    public void draw(Canvas canvas) {
        final int privateFlags = mPrivateFlags;
        final boolean dirtyOpaque = (privateFlags & PFLAG_DIRTY_MASK) == PFLAG_DIRTY_OPAQUE &&
                (mAttachInfo == null || !mAttachInfo.mIgnoreDirtyState);
        mPrivateFlags = (privateFlags & ~PFLAG_DIRTY_MASK) | PFLAG_DRAWN;

        // Step 1, draw the background, if needed
        int saveCount;

        if (!dirtyOpaque) {
            drawBackground(canvas);
        }

        // skip step 2 & 5 if possible (common case)
        final int viewFlags = mViewFlags;
        boolean horizontalEdges = (viewFlags & FADING_EDGE_HORIZONTAL) != 0;
        boolean verticalEdges = (viewFlags & FADING_EDGE_VERTICAL) != 0;
        if (!verticalEdges && !horizontalEdges) {
            // Step 3, draw the content
            if (!dirtyOpaque) onDraw(canvas);

            // Step 4, draw the children
            dispatchDraw(canvas);

            drawAutofilledHighlight(canvas);

            // Overlay is part of the content and draws beneath Foreground
            if (mOverlay != null && !mOverlay.isEmpty()) {
                mOverlay.getOverlayView().dispatchDraw(canvas);
            }

            // Step 6, draw decorations (foreground, scrollbars)
            onDrawForeground(canvas);

            // Step 7, draw the default focus highlight
            drawDefaultFocusHighlight(canvas);

            if (debugDraw()) {
                debugDrawFocus(canvas);
            }

            // we're done...
            return;
        }

        // ...
    }
```

注意到在上面的方法中会调用 `dispatchDraw(canvas)` 方法来分发绘制事件给子控件来完成整个 View 树的绘制。在 View 中，这是一个空的方法，ViewGroup 覆写了这个方法，并在其中调用 `drawChild()` 来完成对指定的 View 的 `draw()` 方法的调用：

```java
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return child.draw(canvas, this, drawingTime);
    }
```

而对于 LinearLayout 这样本身没有绘制需求的控件，没有覆写 `onDraw()` 和  `dispatchDraw(canvas)`  等方法，因为 View 和 ViewGroup 中提供的功能已经足够使用。

## 总结：

上文中，我们介绍了在 Android 系统中整个 View 树的工作的流程，从 DecorView 被加载到窗口中，到测量、布局和绘制三个方法的实现。本质上整个工作的流程就是对 View 树的一个深度优先的遍历过程。

