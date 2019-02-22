# Android：解析 SurefaceView & TextureView

## 1、关于 SurefaceView 和 TextureView

### 1.1 基础

[SurfaceView](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/SurfaceView.java) 以及 [TextureView](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/TextureView.java) 均继承于 `android.view.View`，属于 Android 提供的控件体系的一部分。与普通 View 不同，它们都在独立的线程中绘制和渲染。所以，相比于普通的 ImageView 它们的性能更高，因此常被用在对绘制的速率要求比较高的应用场景中，用来解决普通 View 因为绘制的时间延迟而带来的掉帧的问题，比如用作相机预览、视频播放的媒介等。

相比于普通的 View，SurfaceView 有以下几点优势：

1. SurfaceView 适用于主动刷新，普通的 View 无法进行主动刷新；
2. SurfaceView 通过子线程来更新画面，而普通的 View 需要在主线程中更新画面；
3. 最后就是缓冲的问题，普通的 View 不存在缓存机制，而 SurfaceView 存在缓冲机制。

### 1.2 两种控件的基础使用

#### 1.2.1 TextureView 的使用

TextureView 在 `API 14` 中引入，用来展示流，比如视频和 OpenGL 等的流。这些流可以来自应用进程或者是跨进程的。它只能用在开启了硬件加速的窗口，否则无法绘制任何内容。与 SurefaceView 不同，TextureView 不会创建一个独立的窗口，而是像一个普通的 View 一样。这种区别使得 TextureView 可以移动、转换和做动画等，比如你可以使用 TextureView 的 setAlpha() 方法将其设置成半透明的。

TextureView 的使用非常简单，你只需要获取到它的 SurfaceTexture. 然后就可以使用它来渲染。下面的示例说明了如何使用 TextureView 作为相机的预览控件，

```kotlin
class TextureViewActivity : CommonActivity<ActivityTextureViewBinding>(), TextureView.SurfaceTextureListener {

    private lateinit var camera: Camera
    private lateinit var textureView: TextureView

    override fun getLayoutResId(): Int = R.layout.activity_texture_view

    override fun doCreateView(savedInstanceState: Bundle?) {
        textureView = TextureView(this)
        // Add callback to listen the lifecycle callback for TextureView
        textureView.surfaceTextureListener = this
        binding.cl.addView(textureView)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        camera = Camera.open()
        // Add the surface texture to camera
        camera.setPreviewTexture(surface)
        camera.startPreview()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        // Ignored, Camera does all the work for us
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        // Invoked every time there's a new Camera preview frame
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        // Release everything when texture destroyed
        camera.stopPreview()
        camera.release()
        return true
    }
}
```

TextureView 的 SurfaceTexture 可以通过 `getSurfaceTexture()` 方法或者通过 SurfaceTextureListener 获取到。还有一点很重要的是，SurfaceTexture 只在 TextureView 关联到窗口并且 `onAttachedToWindow()` 被触发的之后可用。因此，强烈建议使用监听的方式来获取 SurfaceTexture 可用的通知。

最后还有一个重要的就是，在同一时刻只能由一个生产者可以使用 TextureView，就是说，当你使用 TextureView 作为相机预览的时候是无法使用 `lockCanvas()` 同时在 TextureView 上面进行绘制的。

除了只能在开启了硬件加速的窗口中使用，TextureView 消费的内存要比 SurfaceView 要多，并伴随着 1-3 帧的延迟。

#### 1.2.2 SurefaceView 的使用

SurfaceView 也用来解决页面刷新频繁的问题，它提供了嵌入在视图层次结构内的专用绘图图层 (Surface)。我们可以用它来控制曲面的格式和大小以及在屏幕上的位置。图层 (Surface) 处于 Z 轴，位于持有 SurfaceView 的窗口之后。SurfaceView 在窗口上开了一个透明的 “洞” 以展示图面。Surface 的排版显示受到视图层级关系的影响，它的兄弟视图结点会在顶端显示。这意味者 Surface 的内容会被它的兄弟视图遮挡，这一特性可以用来放置遮盖物(overlays)(例如，文本和按钮等控件)。注意，如果 Surface 上面有透明控件，那么每次 Surface 变化都会引起框架重新计算它和顶层控件的透明效果，这会影响性能。

可以通过 SurfaceView 的 `getHolder()` 方法获取图层 (Surface)，它通过接口 SurfaceHolder 提供。当 SurfaceView 所在的窗口可见的时候，图层 (Surface) 会被创建。你可以通过实现 `SurfaceHolder.Callback.surfaceCreated(SurfaceHolder) ` 和 ` SurfaceHolder.Callback.surfaceDestroyed(SurfaceHolder)` 监听 Surface 的创建和销毁事件，并且只能在这两个方法之间对图层 (Surface) 进行操作。 SurfaceView 和 SurfaceHolder.Callback 的所有方法都会被主线程调用，所以当在子线程中进行绘制的时候，必须妥善进行线程的同步。

```kotlin
class SurfaceViewActivity : CommonActivity<ActivitySurfaceViewBinding>(), SurfaceHolder.Callback {

    private lateinit var camera: Camera
    private lateinit var surfaceView: SurfaceView
    private lateinit var holder: SurfaceHolder

    override fun getLayoutResId(): Int = R.layout.activity_surface_view

    override fun doCreateView(savedInstanceState: Bundle?) {
        surfaceView = SurfaceView(this)
        // Add callback to listen the lifecycle callback for SurfaceView
        holder = surfaceView.holder
        holder.addCallback(this)
        binding.cl.addView(surfaceView)
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        camera = Camera.open()
        camera.setPreviewDisplay(holder)
        camera.startPreview()
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        // Ignored, Camera does all the work for us
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        camera.stopPreview()
        camera.release()
    }
}
```

就像 TextureView 一样，SurfaceView 也提供了生命周期的回调接口。当我们只需要从 SurfaceView 上面得到一个 SurfaceHolder 实例然后向其中添加回调即可。

上面我们以两种控件在相机中的使用为例，除此之外，你也可以自定义两个控件的字类，然后覆写 `onDraw()` 方法进行简单绘图。然后尝试在线程当中对绘图进行更新，以观察它们在非主线程当中更新页面的表现。

SurfaceView 和 View 一大不同就是 SurfaceView 是被动刷新的，但我们可以控制刷新的帧率，而 View 并且通过`invalidate()` 方法通知系统来主动刷新界面的，但是 View 的刷新是依赖于系统的 VSYSC 信号的，其帧率并不受控制，而且因为 UI 线程中的其他一些操作会导致掉帧卡顿。而对于 SurfaceView 而言，它是在子线程中绘制图形，根据这一特性即可控制其显示帧率，通过简单地设置休眠时间，即可，并且由于在子线程中，一般不会引起 UI 卡顿。

SurfaceView 是通过双缓冲来实现的：对于每一个 SurfaceView 而言，有两个独立的 graphic buffer. 在 Buffer A 中绘制内容，然后让屏幕显示 Buffer A；在下一个循环中，在 Buffer B 中绘制内容，然后让屏幕显示Buffer B，如此往复。而由于这个双缓冲机制的存在，可能会引起闪屏现象。解决办法是：不 `post` 空 buffer 到屏幕：当准备更新内容时，先判断内容是否为空，只有非空时才启动 `lockCanvas()-drawCanvas()-unlockCanvasAndPost()` 这个流程。

### 1.3 区别

TextureView 和 SurfaceView 都继承自 View 类，但是 TextureView 在 Andriod 4.0 之后的 API 中才能使用。SurfaceView 可以通过 `SurfaceHolder.addCallback()` 方法在子线程中更新 UI；TextureView 则可以通过 `TextureView.setSurfaceTextureListener()` 在子线程中更新 UI，能够在子线程中更新 UI 是上述两控件相比于 View 的最大优势。

两者更新画面的方式也有些不同，由于 SurfaceView 的双缓冲功能，可以是画面更加流畅的运行。SurfaceView 自带一个 Surface，这个 Surface 在 WMS 中有自己对应的WindowState，在 SurfaceFlinger 中也会有自己的 Layer。这样的好处是对这个Surface的渲染可以放到单独线程去做，渲染时可以有自己的GL context。但是由于其 Surface 的存在导致画面更新会存在间隔。并且，因为这个 Surface 不在 View hierachy 中，它的显示也不受 View 的属性控制，所以不能进行平移，缩放等变换，也不能放在其它 ViewGroup 中，一些 View 中的特性也无法使用。

TextureView 和 SurfaceView 不同，它不会在 WMS 中单独创建窗口，而是作为 View hierachy 中的一个普通 View，因此可以和其它普通 View 一样进行移动，旋转，缩放，动画等变化。值得注意的是 TextureView 必须在硬件加速的窗口中。它显示的内容流数据可以来自 App 进程或是远端进程。从类图中可以看到，TextureView 继承自 View，它与其它的 View 一样在 View hierachy 中管理与绘制。TextureView 重载了 `draw()` 方法，其中主要 SurfaceTexture 中收到的图像数据作为纹理更新到对应的 HardwareLayer 中。它占用内存比 SurfaceView 高，在 5.0 以前在主线程渲染，5.0 以后有单独的渲染线程。



