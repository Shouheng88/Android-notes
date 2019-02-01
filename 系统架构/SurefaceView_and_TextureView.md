# SurefaceView

## SurefaceView & TextureView

均继承于 android.view.View，源码：

SurfaceView：https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/SurfaceView.java
TextureView：https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/TextureView.java

与普通 View 不同，它们都在独立的线程中绘制和渲染。

### TextureView

TextureView 用来展示流，比如视频和 OpenGL 等的流。这些流可以来自应用进程或者是跨进程的。它只能用在开启了硬件加速的窗口，否则无法绘制任何内容。与 SurefaceView 不同，TextureView 不会创建一个独立的窗口，而是像一个普通的 View 一样。这种区别使得 TextureView 可以移动、转换和做动画等，比如你可以使用 TextureView 的 setAlpha() 方法将其设置成半透明的。

TextureView 的使用非常简单，你只需要获取到它的 SurfaceTexture. 然后就可以使用它来渲染。下面的示例说明了如何使用 TextureView 作为相机的预览控件，

```java
    public class LiveCameraActivity extends Activity implements TextureView.SurfaceTextureListener {
        private Camera mCamera;
        private TextureView mTextureView;

        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // 创建控件，并添加生命周期回调
            mTextureView = new TextureView(this);
            mTextureView.setSurfaceTextureListener(this);
            // 添加到布局中
            setContentView(mTextureView);
        }

        // 当 TextureView 的 SurfaceTexture 可以使用的时候被回调
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mCamera = Camera.open();
            try {
                mCamera.setPreviewTexture(surface);
                mCamera.startPreview();
            } catch (IOException ioe) {
                // 异常处理
            }
        }

        // 当 SurfaceTexture 的缓存大小发生变化的时候被回调
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        // 当 SurfaceTexture 将要被销毁的时候回调到
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mCamera.stopPreview();
            mCamera.release();
            return true;
        }

        // 当 SurfaceTexture 通过 SurfaceTexture 的 updateTexImage() 被更新的时候被回调到
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }
```

TextureView 的 SurfaceTexture 可以通过 `getSurfaceTexture()` 方法或者通过 SurfaceTextureListener 获取到。还有一点很重要的是，SurfaceTexture 只在 TextureView 关联到窗口并且 `onAttachedToWindow()` 被触发的时候可用。因此，强烈建议使用监听的方式来获取 SurfaceTexture 可用的通知。

最后还有一个重要的就是，在同一时刻只能由一个生产者可以使用 TextureView，就是说，当你使用 TextureView 作为相机预览的时候是无法使用 `lockCanvas()` 同时在 TextureView 上面进行绘制的。

除了只能在开启了硬件加速的窗口中使用，TextureView 消费的内存要比 SurfaceView 要多，并伴随着 1-3 帧的延迟。

### SurefaceView

SurfaceView 也用来解决页面刷新频繁的问题，它提供了嵌入在视图层次结构内的专用绘图图面 (Surface)。我们可以用它来控制曲面的格式和大小以及在屏幕上的位置。

图面 (Surface) 处于 Z 轴，位于持有 SurfaceView 的窗口之后。SurfaceView 在窗口上开了一个透明的 “洞” 以展示图面。

### Left

TextureView和SurfaceView都是继承自View类的，但是TextureView在Andriod4.0之后的API中才能使用。SurfaceView可以通过SurfaceHolder.addCallback方法在子线程中更新UI，TextureView则可以通过TextureView.setSurfaceTextureListener在子线程中更新UI，个人认为能够在子线程中更新UI是上述两种View相比于View的最大优势。

但是，两者更新画面的方式也有些不同，由于SurfaceView的双缓冲功能，可以是画面更加流畅的运行，但是由于其holder的存在导致画面更新会存在间隔（不太好表达，直接上图）。并且，由于holder的存在，SurfaceView也不能进行像View一样的setAlpha和setRotation方法，但是对于一些类似于坦克大战等需要不断告诉更新画布的游戏来说，SurfaceView绝对是极好的选择。但是比如视频播放器或相机应用的开发，TextureView则更加适合。





