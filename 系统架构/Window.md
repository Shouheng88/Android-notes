# Window

View 体系的那篇文章 https://juejin.im/post/5bc5e3fbe51d450e7e51befa

添加一个 View

        WindowManager wm = getWindowManager();

        Button button = new Button(getContext());
        button.setText("My Window Button");

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, Color.TRANSPARENT);
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        params.gravity = Gravity.CENTER;
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION;

        wm.addView(button, params);

WindowManager 实现了 ViewManager 接口

这里的 type 和 flag 是有限制的，具体可以看官方文档

1. Q :Window 和 Activity 之间的关系？

Activity#handleResumeActivity() 方法中从 Activity 中获取 WindowManager

            ViewManager wm = a.getWindowManager();
            WindowManager.LayoutParams l = r.window.getAttributes();

Activity 会从内部的 mWindow 中获取 WindowManager，Window 中的 WindowManager 初始化过程：

    public void setWindowManager(WindowManager wm, IBinder appToken, String appName,
            boolean hardwareAccelerated) {
        // ...
        if (wm == null) {
            wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        }
        mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);
    }

    public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        return new WindowManagerImpl(mContext, parentWindow);
    }

这里 WindowManagerImpl，WindowManagerImpl，会把所有任务都委托给 WindowManagerGlobal

    public final class WindowManagerImpl implements WindowManager {
        private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();

        // ...
    }

WindowManagerGlobal，全局单例：

    public static WindowManagerGlobal getInstance() {
        synchronized (WindowManagerGlobal.class) {
            if (sDefaultWindowManager == null) {
                sDefaultWindowManager = new WindowManagerGlobal();
            }
            return sDefaultWindowManager;
        }
    }

所以增删 Window 的操作可以直接看 WindowManagerGlobal 部分的代码。

WindowManagerGlobal 内部的三个列表：

    private final ArrayList<View> mViews = new ArrayList<View>();
    private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();
    private final ArrayList<WindowManager.LayoutParams> mParams = new ArrayList<WindowManager.LayoutParams>();
    private final ArraySet<View> mDyingViews = new ArraySet<View>();

增删 View 的时候操作的就是这些列表？

添加 View 的时候：

    public void addView(View view, ViewGroup.LayoutParams params, Display display, Window parentWindow) {
            // ... 无关代码
            // new 出一个 ViewRootImpl 来对 View 进行绘制
            root = new ViewRootImpl(view.getContext(), display);
            view.setLayoutParams(wparams);

            mViews.add(view);
            mRoots.add(root);
            mParams.add(wparams);
            try {
                // 会在 ViewRootImpl 的 setView() 方法中对 View 进行测量和绘制
                root.setView(view, wparams, panelParentView);
            } catch (RuntimeException e) {
                if (index >= 0) {
                    removeViewLocked(index, true);
                }
                throw e;
            }
    }

在 setView() 方法中会：

会调用 requestLayout() 方法进行布局，然后，并最终调用 performTraversals() 完成对整个 View 树进行遍历，并依次调用各控件的 onMeasre onLayout onDraw 来将视图绘制出来

最后

                    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(), mWinFrame,
                            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                            mAttachInfo.mOutsets, mAttachInfo.mDisplayCutout, mInputChannel);

通过 mWindowSession 来将视图呈现给用户（mWindowSession 在 ViewRootImpl 定义）

这里的 mWindowSession 是怎么来的呢？ [IWindowSession](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/IWindowSession.aidl) 从 [IWindowManager](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/IWindowManager.aidl) 的 `openSession()` 方法中获取到，两者都是 AIDL 文件。IWindowManager 由 [ServiceManager](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/ServiceManager.java) 中获取的 `window` 服务的 `Binder` 对象得到，就是得到了远程的窗口服务。然后，再使用该窗口服务来打开对话。该窗口服务的定义是 [WindowManagerService](https://android.googlesource.com/platform/frameworks/base/+/b267554/services/java/com/android/server/wm/WindowManagerService.java)。其内部会实例化一个 `Session ` 并将其返回，这就是 IWindowSession。

从上面可以看出，添加 Window 的过程中需要使用 IPC，请求远程的窗口服务来最终完成添加窗口的操作。


移除 View：

    public void removeView(View view, boolean immediate) {
        // ... 无关代码
        synchronized (mLock) {
            // 返回要移除的 View 的索引
            int index = findViewLocked(view, true);
            View curView = mRoots.get(index).getView();
            // 移除 View
            removeViewLocked(index, immediate);
            if (curView == view) {
                return;
            }

            throw new IllegalStateException("Calling with view " + view + " but the ViewAncestor is attached to " + curView);
        }
    }

    private void removeViewLocked(int index, boolean immediate) {
        // 获取对应的 ViewRootImple
        ViewRootImpl root = mRoots.get(index);
        View view = root.getView();

        if (view != null) {
            InputMethodManager imm = InputMethodManager.getInstance();
            if (imm != null) {
                imm.windowDismissed(mViews.get(index).getWindowToken());
            }
        }
        // 调用 ViewRootImpl 的 die() 方法
        boolean deferred = root.die(immediate);
        if (view != null) {
            view.assignParent(null);
            if (deferred) {
                // 将其添加到正在销毁的视图的队列中
                mDyingViews.add(view);
            }
        }
    }

ViewRootImpl 的 die() 方法会根据布尔类型变量 `immediate` 决定是立即执行 doDie() 还是把消息发送给 Handler 让它来调用 doDie()

本质上都会调用 doDie() 来移除视图

doDie() 方法主要：

调用 

调用 WindowManagerGlobal.getInstance().doRemoveView(this); 从上述列表中将视图相关的内容移除


Window 更新

    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }

        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams)params;

        view.setLayoutParams(wparams);

        synchronized (mLock) {
            int index = findViewLocked(view, true);
            ViewRootImpl root = mRoots.get(index);
            mParams.remove(index);
            mParams.add(index, wparams);
            root.setLayoutParams(wparams, false);
        }
    }

最终会调用 ViewRootImpl 的 scheduleTraversals(); 方法从新对 View 树进行测量布局和绘制，从而整个视图就以新的面貌呈现给用户了。



