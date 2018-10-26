# View 体系详解：自定义控件

在自定义控件之前，我们应该至少了解 Android 中控件体系的基础内容，你可以通过下面的三篇文章来获取这方面的知识：

1. [《View 体系详解：坐标系、滑动、手势和事件分发机制》](https://juejin.im/post/5bbb5fdce51d450e942f6be4)
2. [《View 体系详解：View 的工作流程》](https://shouheng88.github.io/2018/10/14/View%20%E4%BD%93%E7%B3%BB%E8%AF%A6%E8%A7%A3%EF%BC%9AView%20%E7%9A%84%E5%B7%A5%E4%BD%9C%E6%B5%81%E7%A8%8B/)
3. [《Android 动画详解：属性动画、View 动画和帧动画》](https://juejin.im/post/5bb884506fb9a05cf9084857)

根据我们的业务需求我们可以有多种方式来实现一个自定义控件，当然，最好能够复用已有的控件。下面是几种常见的方式：

1. 通过继承系统控件进行自定义；
2. 通过布局文件自定义组合控件；
3. 通过继承 View 进行自定义。

实际上，前面的两种没有太多需要介绍的东西，毕竟，就像之前有人说的，现在的 Android 开发者哪有什么新手，所以，这里我们只大致介绍一下，然后对一些细节的内容进行整理。对于第三种方式，内容会比较多一些，我们会尽量更加详尽地介绍。

## 1、通过继承系统控件进行自定义

这种方式通过继承某个系统的控件，以在其基础之上实现我们需要的功能，这算是 COST 比较小的一种方式。当然，具体如何去实现是根据个人的需求来定的，我们无法一一俱到。我觉得做好这种自定义的前提是对 Android 系统 View 体系的理解，你可以通过前面的一些文章来了解这块的内容。

下面我们以实现正方形的布局为例来说明如何实现这种控件的定义：

    public class SquareFrameLayout extends FrameLayout {
        // ...
        @SuppressWarnings("unused")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), getDefaultSize(0, heightMeasureSpec));
            int childWidthSize = getMeasuredWidth();
            int childHeightSize = getMeasuredHeight();
            heightMeasureSpec = widthMeasureSpec = MeasureSpec.makeMeasureSpec(childWidthSize, MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

上面，我们覆写了 `FrameLayout` 的测量方法，在最终确定测量结果的时候调用了基类的 `onMeasure()` 并把为高度和宽度参数传入相同的值，以实现高度和宽度相同。

除了实现 `onMeasure()` 方法，我们还可以实现 `onDraw()`，或者拦截触摸事件，或者弹出一个 `Window` 等来实现特定的功能。

## 2、通过布局文件自定义组合控件

### 2.1 组合控件的实现方式

这种方式也是比较常用的一种方式，它的做法是：先通过布局文件组合已有的控件实现需要的功能，然后通过覆写现有的容器控件，在加载控件的时候把当前的容器作为其容器，最后把整个控件的逻辑用代码实现即可。这种方式不会降低布局的复杂程度，效率比直接绘制的方式低，但是更加方便，用得也最多，其好处是把在多个地方都用到的组合控件封装起来，统一代码实现，降低维护成本。

    public class EmptyView extends LinearLayout {
        // ...
        public EmptyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init(context, attrs);
        }
        private void init(Context context, AttributeibuteSet) {
            View root = LayoutInflater.from(context).inflate(R.layout.widget_empty_view, this, true);
        }
    }   

像上面这样，我们在布局文件 `widget_empty_view` 中写好了这个控件的布局，然后通过 `LayoutInflater` 获取并将其父容器指向当前的控件 `this`，然后我们可以从 `root` 中获取布局中的控件，并对其进行处理即可。

上面的方式我们不去详细介绍，这里对于自定义控件的布局属性进行一些整理。

### 2.2 布局属性

我们可以为自定义控件指定一些属性，主要用在在 `xml` 中。首先，我们在 `attr.xml` 中使用 `declare-styleable` 标签，这里的 `name` 属性是我们自定义控件的名称，然后内部的子标签用来指定自定义属性和属性的值类型。

    <declare-styleable name="EmptyView">
        <attr name="title" format="string"/>
        <!-- ... -->
    </declare-styleable>

然后我们可以在自定义控件中通过下面的代码来获取属性值，并赋值给控件。这里的 `attributeSet` 来自自定义控件的构造方法：

    TypedArray attr = context.obtainStyledAttributes(attributeSet, R.styleable.EmptyView, 0, 0);
    String bottomTitle = attr.getString(R.styleable.EmptyView_title);
    attr.recycle();

上面我们给出的是自定义属性的一个非常基本的用法，如果想要对自定义属性有更多的了解，Android 系统控件的源码是非常好的参考资料。有拿不准的地方可以直接参考系统控件的源码，这里不再做更多的说明。

## 3、通过继承 View 进行自定义



