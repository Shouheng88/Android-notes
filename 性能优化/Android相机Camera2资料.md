# Android 相机开发资料梳理

使用相机的前提是获取到 CameraManager 对象，然后从 CameraManager 中获取 CameraDevice 对象。每个 CameraDevice 对象包含了一系列的属性信息。这些信息可以从 CameraCharacteristics 对象中获取。而 CameraCharacteristics 对象又通过 `getCameraCharacteristics(String)` 方法得到。

要拍摄照片或者视频，需要先使用一系列的 Surface 调用 `createCaptureSession(SessionConfiguration)` 方法创建相机拍摄会话。每个 Surface 需要先被只适当的大小和格式，以与相机设别中的尺寸和格式匹配。目标 Surface 可以通过很多的类来获取，包括  SurfaceView, SurfaceTexture (通过 `Surface(SurfaceTexture)` 获取), MediaCodec, MediaRecorder, Allocation 和 ImageReader.

一般，相机预览图片会被设置给 SurfaceView 或者 TextureView（通过它的 SurfaceTexture）. 拍摄 JPEG 图片或者 DngCreator 的 RAW 缓存可以通过 ImageReader 和 JPEG 或者 RAW_SENSOR 格式。对于 RenderScript, OpenGL ES 或者直接通过 native 代码操作的数据，最好分别通过 Allocation 和 YUV Type, SurfaceTexture, ImageReader 和 YUV_420_888 格式完成。

然后应用需要创建 CaptureRequest 来设置相机设备拍摄图片需要的各种参数。该请求还要列出哪些输出的 Surface 应该被用作目标 Surface. CameraDevice 又一个工厂方法用来创建指定用途的请求构建器。

请求创建完毕之后，可以交给会话作为一次拍摄或者多次连续拍摄使用。连续请求的优先级低于拍摄。

请求处理完毕，CameraDeive 会创建一个 TotalCaptureResult 对象, 它包含了相机设备拍摄时的信息和配置。相机同时会将图片数据片发送给请求包含的 Surface. 这些都是异步执行的。

## 1、关于 SurfaceView 和 TextureView

一般当开启相机的预览的时候，我们会在这两个接口的回调方法中打开相机，并将预览相关的 Surface 和 SurfaceTexture 赋值给 Camera. 我们需要了解它的回调方法都是在什么时候被调用，以便于我们准确地对 CameraPreview 进行封装。

### 1.1 SurfaceView

#### 1.1.1 关于 SurfaceHolder.Callback 接口

`SurfaceHolder.Callback` 用来获取 surface 的改变的信息，surface 只在 `surfaceCreated()` 和 `surfaceDestroyed()` 之间可用。

1. `surfaceCreated(SurfaceHolder)`：surface 创建的时候调用，只有一个线程可以对 Surface 进行绘制，所以如果正常的绘制在其他线程的时候，就不应该在这里对 Surface 进行绘制
2. `surfaceChanged(SurfaceHolder, int, int, int)`：当 surface 的尺寸和大小发生变化的时候调用，应该在这个时刻更新 surface 上的图像，该方法至少被回调一次。
3. `surfaceDestroyed(SurfaceHolder)`：该方法结束之后就不能再对 surface 进行绘制了。

### 1.2 TextureView

#### 1.2.1 关于 TextureView.SurfaceTextureListener 接口

`TextureView.SurfaceTextureListener` 当 SurfaceTexture 发生变化的时候会被回调。

1. `onSurfaceTextureAvailable(SurfaceTexture, int, int)`：当 SurfaceTexture 可用的时候被回调。
2. `onSurfaceTextureSizeChanged(SurfaceTexture, int, int)`：当 SurfaceTexture 的 buffers 大小改变的时候被回调。
3. `boolean onSurfaceTextureDestroyed(SurfaceTexture)`：SurfaceTexture 将要被销毁的时候回调，返回 true 的时候，SurfaceTexture 将无法再进行绘制，返回 false 的时候，需要用户手动释放。一般情况下都是返回 true.
4. `onSurfaceTextureUpdated(SurfaceTexture)`：当 SurfaceTexture 的 `updateTextImage()` 被调用的时候被回调。

#### 1.2.2 SurfaceTexture 的方法

1. `setDefaultBufferSize(int, int)`：设置图片缓存的默认的大小，图片的生产者可能会重写这个值，此时重写的值将会被调用。

## 2、Camera 2 的一些类

### 2.0 CameraManager

对应于相机的系统服务，连接到 CameraDevice.

|公共方法||
|:-|:-|
|CameraCharacteristics|`getCameraCharacteristics(String cameraId)` 获取相机设备的功能|
|String[]|`getCameraIdList()` 获取相机设备可用的相机 id 列表，也包括其他应用可能正在使用的相机|
|void|`openCamera(String cameraId, CameraDevice.StateCallback callback, Handler handler)` 打开指定 id 的相机|
|void|`openCamera(String cameraId, Executor executor, CameraDevice.StateCallback callback)` 打开指定 id 的相机|
|void|`registerAvailabilityCallback(Executor executor, CameraManager.AvailabilityCallback callback)` 注册当相机设备可用时的回调|
|void|`registerAvailabilityCallback(CameraManager.AvailabilityCallback callback, Handler handler)` 注册当相机设备可用时的回调|
|void|`registerTorchCallback(CameraManager.TorchCallback callback, Handler handler)` 注册用来获取火炬模式状态的回调|
|void|`registerTorchCallback(Executor executor, CameraManager.TorchCallback callback)` 注册用来获取火炬模式状态的回调|
|void|`setTorchMode(String cameraId, boolean enabled)` 设置闪光灯单元的火炬模式，而不用打开指定的相机|
|void|`unregisterAvailabilityCallback(CameraManager.AvailabilityCallback callback)` 移除之前添加的回调|
|void|`unregisterTorchCallback(CameraManager.TorchCallback callback)` 移除之前添加的回调|

### 2.1 CameraDevice

CameraDevice 通过 `CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL` 定义了一系列的支持的等级，下面是支持的的等级的一些说明：

1. `CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY`：相机设备处于向后兼容模式，支持最低的 camera2 API;
2. `CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED`：此时 Camera2 的特性基本等同于 Camera；
3. `CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL`：设备使用的是可移动的相机，特性近似 `CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED`；
4. `CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_FULL` 和 `CameraMetadata#INFO_SUPPORTED_HARDWARE_LEVEL_3`：相机的特性比 Camera API 略多。

如果你的应用需要最高级别的操作，需要在 manifest 中声明 `android.hardware.camera.level.full` 特性。

|公共方法|说明|
|:-|:-|
|abstract void|`close()` 尽快关闭该相机设备的连接|
|CaptureRequest.Builder|`createCaptureRequest(int templateType, Set<String> physicalCameraIdSet)` 创建新的拍摄请求的 CaptureRequest.Builder 构建者，并通过指定用途的 templateType 初始化|
|abstract CaptureRequest.Builder|`createCaptureRequest(int templateType)` 创建新的拍摄请求的 CaptureRequest.Builder 构建者，并通过指定用途的 templateType 初始化。传入的是一个枚举，但不是所有的设备都支持所有的枚举类型。枚举的值包括：1.`TEMPLATE_PREVIEW`: 创建适用于相机预览的请求；2.`TEMPLATE_STILL_CAPTURE`: 创建适用于静态图像的请求；3.`TEMPLATE_RECORD`: 创建适用于视频拍摄的请求；4.`TEMPLATE_VIDEO_SNAPSHOT`: 创建适用于拍摄视频时的静态图像的请求；5.`TEMPLATE_ZERO_SHUTTER_LAG`: 创建适用于零快门静态拍摄的请求；6.`TEMPLATE_MANUAL`:|
|void|`createCaptureSession(SessionConfiguration config)` 使用包含所有配置新的 SessionConfiguration 创建新的 CameraCaptureSession|
|abstract void|`createCaptureSession(List<Surface> outputs, CameraCaptureSession.StateCallback callback, Handler handler)` 创建新的的相机拍摄会话。可以在回调的方法中得到该 CameraCaptureSession。|
|abstract void|`createCaptureSessionByOutputConfigurations(List<OutputConfiguration> outputConfigurations, CameraCaptureSession.StateCallback callback, Handler handler)` 创建新的的相机拍摄会话|
|abstract void|`createConstrainedHighSpeedCaptureSession(List<Surface> outputs, CameraCaptureSession.StateCallback callback, Handler handler)` 创建告诉拍摄会话|
|abstract CaptureRequest.Builder|`createReprocessCaptureRequest(TotalCaptureResult inputResult)` 通过 TotalCaptureResult 创建新的 CaptureRequest 的 CaptureRequest.Builder|
|abstract void|`createReprocessableCaptureSession(InputConfiguration inputConfig, List<Surface> outputs, CameraCaptureSession.StateCallback callback, Handler handler)` 通过输入 Surface 配置和输出 Surface 创建相机拍摄会话|	
|abstract void|`createReprocessableCaptureSessionByConfigurations(InputConfiguration inputConfig, List<OutputConfiguration> outputs, CameraCaptureSession.StateCallback callback, Handler handler)` 通过输入 Surface 配置和输出 Surface 创建相机拍摄会话|
|abstract String|`getId()` 获取相机设备的 Id|
|boolean|`isSessionConfigurationSupported(SessionConfiguration sessionConfig)` 该相机设备是否支持指定的 SessionConfiguration|

### 2.2 CameraCharacteristics 

存储了 `CameraDevice` 的属性信息，使用 `CameraManager` 的 `getCameraCharacteristics()` 方法传入了相机的 id 之后即可得到 CameraCharacteristics. 然后，我们可以使用 CameraCharacteristics 的方法获取相机的属性。这类的方法有：

|返回类型|方法|
|:-|:-|
|`<T> T`|`get(Key<T> key)`|
|`List<Key<?>>`|`getKeys()`|
|`List<CaptureRequest.Key<?>>`|`getAvailableSessionKeys()`|
|`List<CaptureRequest.Key<?>>`|`getAvailablePhysicalCameraRequestKeys()`|
|`List<CaptureRequest.Key<?>>`|`getAvailableCaptureRequestKeys()`|
|`List<CaptureResult.Key<?>>`|`getAvailableCaptureResultKeys()`|

这里的属性的键 Key 以一组静态常量的形式分别定义在了 CameraCharacteristics, CaptureRequest 和 CaptureResult 中。其中的很多的常量，可以用来对相机的参数进行设置或者获取相机的参数。此外还有一个 TotalCaptureResult，它们都继承自 CameraMetadata，而 CaptureRequest 又是 TotalCaptureResult 的基类。

可以参考附录来了解我们都有哪些属性可以使用。

### 2.3 CameraCaptureSession

#### 2.3.1 CameraCaptureSession

CameraDevice 的拍摄请求用来使用相机拍摄图片或者在之前的会话中处理拍摄的图片。

CameraCaptureSession 可以通过给 `CameraDevice#createCaptureSession()` 设置一系列的 Surface 实现。或者通过将 InputConfiguration 以及一系列的 Surface 赋值给 `CameraDevice#createReprocessableCaptureSession()` 方法来创建一个可以重复处理的拍摄会话。一旦会话被创建，会话将会一直处于激活的状态，直到该相机设备的新的会话被创建或者相机设备关闭。

所有的拍摄会话都可以用来从相机拍摄图片，但是只有可以复用的拍摄会话能使用该相机之前的会话处理图片。

创建会话是一个耗时的操作，可能需要花费几百毫秒的时间，因为需要配置相机设备的内部管道，并且需要申请内存缓存以发送图片到指定的目标。因此，该过程是通过异步来完成的，并且 `CameraDevice#createCaptureSession()` 和 `CameraDevice#createReprocessableCaptureSession()` 会把即将可用的 CameraCaptureSession 发送给监听的 `CameraCaptureSession.StateCallback#onConfigured()` 回调。如果配置过程发生了错误，那么 `CameraCaptureSession.StateCallback#onConfigureFailed()` 将会被触发，并且会话将会不可用（处于激活状态）。

当新的会话被创建，之前的会话将会关闭，并且与之关联的 `StateCallback#onClosed()` 回调会被触发。一旦会话被关闭，那么所有的会话方法将会抛出 IllegalStateException 异常。

关闭的会话会清理所有的重复的请求（跟 `stopRepeating()` 被调用的效果一样），但是在新创建的会话占用相机设备之前，会话会正常完成所有的正在处理的拍摄请求。

|公共方法|说明|
|:-|:-|
|abstract void |`abortCaptures()` 丢弃所有正在处理和等待处理的请求|
|abstract int|`capture(CaptureRequest request, CameraCaptureSession.CaptureCallback listener, Handler handler)` 提交一个图片拍摄请求。该请求定义了拍摄图片的所有的参数。每个请求会产生一个 CaptureResult。这里的回调会在请求被处理的时候调用。handler 是回调被触发的线程。|
|abstract int|`captureBurst(List<CaptureRequest> requests, CameraCaptureSession.CaptureCallback listener, Handler handler)` 提交一系列按顺序拍摄的请求|
|int|`captureBurstRequests(List<CaptureRequest> requests, Executor executor, CameraCaptureSession.CaptureCallback listener)` 提交一系列按顺序拍摄的请求|
|int|`captureSingleRequest(CaptureRequest request, Executor executor, CameraCaptureSession.CaptureCallback listener)` 提交一个图片拍摄请求|
|abstract void|`close()` 异步地关闭该拍摄请求|
|abstract void|`finalizeOutputConfigurations(List<OutputConfiguration> outputConfigs)` 回收输出配置|
|abstract CameraDevice|`getDevice()` 获取创建该会话的相机设备|
|abstract Surface|`getInputSurface()` 获取与可重新拍摄请求关联的输入的 Surface|
|abstract boolean|`isReprocessable()` 该应用是否可以通过该拍摄会话提交请求|
|abstract void|`prepare(Surface surface)` 为输出 Surface 预申请所有的缓存|
|abstract int|`setRepeatingBurst(List<CaptureRequest> requests, CameraCaptureSession.CaptureCallback listener, Handler handler)` 通过该会话获取一个可以不断拍摄的请求|
|int|`setRepeatingBurstRequests(List<CaptureRequest> requests, Executor executor, CameraCaptureSession.CaptureCallback listener)` 通过该会话获取一个可以不断拍摄的请求|
|abstract int|`setRepeatingRequest(CaptureRequest request, CameraCaptureSession.CaptureCallback listener, Handler handler)` 通过该会话获取一个可以不断拍摄的请求|
|int|`setSingleRepeatingRequest(CaptureRequest request, Executor executor, CameraCaptureSession.CaptureCallback listener)` 通过该会话获取一个可以不断拍摄的请求|
|abstract void|`stopRepeating()` 取消所有正在进行的重复拍摄|
|void|`updateOutputConfiguration(OutputConfiguration config)` 配置完成后更新 OutputConfiguration|

#### 2.3.2 CameraCaptureSession.CaptureCallback

用来获取提交给 CameraDevice 的 CaptureRequest 的处理过程的回调。

当请求开始拍摄或者拍摄完成之后该回调将会被触发。当发生了错误的时候，

|方法||
|:-|:-|
|void|`onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber)` 当请求的一个 buffer 无法被发送到指定的 Suface 的时候该方法会被回调|
|void|`onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)` 当图片拍摄请求完成并且拍摄结果可用的时候该方法会被回调|
|void|`onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure)` 当相机设备无法生成拍摄结果（发生了错误）的时候这个方法会被回调，而不是 `onCaptureCompleted(CameraCaptureSession, CaptureRequest, TotalCaptureResult)`|
|void|`onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult)` 图片拍摄过程中被回调|
|void|`onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId)` 该方法的回调与其他方法独立，当一个拍摄序列被丢弃的时候触发|
|void|`onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber)` 该方法的回调与其他方法独立，当图片开始曝光或者相机设备开始处理输入的图片的时候被触发|
|void|`onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber)` 相机设备开始拍摄的时候回调|

### 2.3.3 CameraCaptureSession.StateCallback

用来获取相机拍摄会话状态的回调。

|公共方法||
|:-|:-|
|void|`onActive(CameraCaptureSession session)` 当会话开始处理拍摄请求的时候回调|
|void|`onCaptureQueueEmpty(CameraCaptureSession session)` 相机设备的输入图片序列为空，并且准备接收下一个请求的时候被调用|
|void|`onClosed(CameraCaptureSession session)` 会话关闭的时候被调用|
|abstract void|`onConfigureFailed(CameraCaptureSession session)` 会话无法被配置或者请求的时候被调用|
|abstract void|`onConfigured(CameraCaptureSession session)` 相机设备完成配置，会话可以开始处理拍摄请求时被回调|
|void|`onReady(CameraCaptureSession session)` 每当会话没有拍摄请求需要处理的时候被调用|
|void|`onSurfacePrepared(CameraCaptureSession session, Surface surface)` 用于输出 Surface 的预申请 buffer 完成的时候被调用|

### 2.4 CaptureRequest

#### 2.4.1 CaptureRequest

包含了拍摄的配置的信息，比如硬件的信息、管道、算法和输出的 buffer 等，也包含了用来发送图片数据的目标 Suface.

可以通过 `CameraDevice#createCaptureRequest()` 得到 CaptureRequests 构建者，然后使用构建者创建 CaptureRequests. 

CaptureRequests 可以用作 `CameraCaptureSession#capture()` 或者 `CameraCaptureSession#setRepeatingRequest()` 大参数来从相机中拍摄图片。

每个请求可以指定不同的目标 Surfaces 子集，然后相机可以把拍摄到的数据发送到这些目标 Surface 上面。请求用到的所有的 Surface 必须包含再最好一次调用 `CameraDevice#createCaptureSession()` 时传入的 Surface 中。例如，预览的请求必须包含 SurfaceView 或 SurfaceTexture 的 Surface，但是高分辨率的静态拍摄必须包含从 ImageReader 中得到的 Surface，并且要使用高分辨率的 JPEG 配置。

可再加工的拍摄请求允许之前配设的图片被发送给相机进一步处理。这种请求可以通过 `CameraDevice#createReprocessCaptureRequest()` 得到，然后配合可以再加工的拍摄会话一起使用，该会话可以通过 `CameraDevice#createReprocessableCaptureSession()` 得到。

#### 2.4.2 CaptureRequest.Builder

|公共方法||
|:-|:-|
|void|`addTarget(Surface outputTarget)` 添加一个 Surface 到该请求的 Surface 列表中。添加的 Surface 必须是添加到 `CameraDevice#createCaptureSession()` 中的 Surface|
|CaptureRequest|`build()` 使用目标的 Surface 和设置构建一个请求|
|<T> T|`get(Key<T> key)` 获取请求字段的值|
|<T> T|`getPhysicalCameraKey(Key<T> key, String physicalCameraId)` 指定物理相机 id 的请求字段的值|
|void|`removeTarget(Surface outputTarget)`|
|<T> void|`set(Key<T> key, T value)` 设置请求的字段的值|
|<T> CaptureRequest.Builder|`setPhysicalCameraKey(Key<T> key, T value, String physicalCameraId)` 设置请求的字段的值|
|void|`setTag(Object tag)` 为请求添加一个 tag|

这里的 Key 是 `CaptureRequest.Key` 类型的，它们是一些定义在 CaptureRequest 中的常量，可以参考附录了解。

### 2.5 CaptureFailure

单个图片拍摄请求失败的信息的封装类，可以使用它获取失败的信息。不论是全部失败还是部分失败，失败的信息都会通过这个类封装。你可以使用 `getReason()` 获取具体的失败的原因。

收到 CaptureFailure 意味着与 frame 关联的 metadata 丢失了，也就是无法通过这个 CaptureResult 得到拍摄结果。

|公共方法||
|:-|:-|
|long|`getFrameNumber()` 获取与当前失败的请求关联的 frame 的号码|
|int|`getReason()` 用来得到失败的原因，不论是内部错误还是用户操作的问题|
|CaptureRequest|`getRequest()` 获取与当前失败的关联的请求|
|int|`getSequenceId()` 获取当前失败的请求的序列 id|
|boolean|`wasImageCaptured()` 获取是否通过相机得到了图片|

### 2.6 CaptureResult

图片拍摄结果的子集，包含了一些最终的配置的子集，比如硬件信息、算法等，也包含了设备的 metadata 等信息。

|公共方法||
|:-|:-|
|<T> T|`get(Key<T> key)` 获取拍摄结果的值|
|long|`getFrameNumber()` 获取与该结果关联的 frame 号|
|List<Key<?>>|`getKeys()` 返回可用 key|
|CaptureRequest|`getRequest()` 获取与该结果对应的请求|
|int|`getSequenceId()` 获取序列号|

这里的 Key 指的是 CaptureResult.Key 类型的，它们被以一组常量的形式定义在 CaptureResult 中。参考附录了解更多。

### 2.7 DngCreator

该类提供了写 DNG 文件原始的像素数据的函数。该类设计用来配合 ImageFormat.RAW_SENSOR 缓存，或者 Bayer 类型的像素数据。DNG metadata tags 是通过 CaptureResult 对象得到或者直接设置的。

DNG 文件格式是跨平台文件格式，用来存储像素数据。它允许像素数据定义在用户自定义的颜色空间。更多关于 DNG 文件格式的内容可以参考：https://wwwimages2.adobe.com/content/dam/acom/en/products/photoshop/pdfs/dng_spec_1.4.0.0.pdf。

### 2.8 TotalCaptureResult

单词图片拍摄的完整结果，包含了一些最终的配置的子集，比如硬件信息、算法等，也包含了设备的 metadata 等信息。

TotalCaptureResult 由 CameraDevice 处理 CaptureRequest 之后得到。拍摄请求的所有属性信息都可以从它当中获取到。

对于逻辑多相机设备，如果 CaptureRequest 包含了物理相机的 Surface，那么对应的 TotalCaptureResult 对象将包含物理相机的 metadata。物理相机的 id 到结果的 metadata 之间的映射可以通过 getPhysicalCameraResults() 得到。如果请求的 Surface 是针对逻辑相机的，那么将不会包含物理相机的 metadata. 

|公共方法||
|:-|:-|
|List<CaptureResult>|`getPartialResults()` 获取组成完整结果的 CaptureResult|
|Map<String, CaptureResult>|`getPhysicalCameraResults()` 物理相机到各自的 CaptureResult 之间的映射。多逻辑摄像机设备可以调用此函数，这些设备是具有 REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA 功能的逻辑多摄像机功能。调用 `CameraCharacteristics#getPhysicalCameraIds()` 返回支持逻辑摄像机的非空物理设备集|

## 3、一些相关的类

### 3.1 CamcorderProfile

检索摄像机应用程序的预定义摄像机配置文件设置。这些设置是只读的。分成两种类型：针对音频的和视频的。每个配置文件指定以下参数集：

1. 文件输出格式
2. 视频编解码器格式
3. 视频比特率，以每秒位数为单位
4. 视频帧速率，以每秒帧数为单位
5. 视频帧宽和高度，
6. 音频编解码器格式
7. 音频比特率，以每秒位数为单位，
8. 音频采样率
9. 录制的音频通道数。

更多内容可以参考附录部分。

### 3.2 ImageReader

用来直接访问渲染到 Surface 上面的图片数据。几款 Android 媒体 API 将 Surface 作为渲染的目标，包括 MediaPlayer, MediaCodec, CameraDevice, ImageWriter and RenderScript Allocations。每种源所使用需要的尺寸和格式可能不同，需要参考相应文档。

图片数据被存储在 Image 中，并且多个 Image 可以同时访问。能够同时访问的 Image 的最大值通过构造函数的 maxImages 参数指定。通过 Surface 发送给 ImageReader 的数据将被放进队列中，直到 `acquireLatestImage()` 或者 `acquireNextImage()` 方法被调用。由于内存限制，如果 ImageReader 获取和释放 Images 的速率以与生产速率不相当的化，那么图像源最终会在尝试停止或丢弃图像。

|公共方法||
|:-|:-|
Image|`acquireLatestImage()` Image 从 ImageReader 的队列中 获取最新信息，删除旧版本 Image。
Image|`acquireNextImage()` 从 ImageReader 的队列中获取下一个Image。
void|`close()` 释放与此 ImageReader 关联的所有资源。
void|`discardFreeBuffers()` 丢弃此 ImageReader 拥有的所有空闲缓冲区。
int|`getHeight()` 默认高度Image，以像素为单位。
int|`getImageFormat()` 默认 ImageFormat 值为Image。
int|`getMaxImages()` 可以随时从 ImageReader 获取的最大图像数（例如，with acquireNextImage()）。
Surface|`getSurface()` 得到一个 Surface 可用于生产 Image 的东西 ImageReader。
int|`getWidth()` 默认宽度Image，以像素为单位。
static ImageReader|`newInstance(int width, int height, int format, int maxImages, long usage)` 为所需大小，格式和消费者使用标志的图像创建新的阅读器。这里的 maxImages 表示能够从该 ImageReader 中同时获取的图片数量的最大值。
static ImageReader|`newInstance(int width, int height, int format, int maxImages)` 为所需大小和格式的图像创建新的 ImageReader。
void|`setOnImageAvailableListener(ImageReader.OnImageAvailableListener listener, Handler handler)` 注册监听，监听会 ImageReader 中新的图片可用的时候被触发。可以在回调被触发的时候从 ImageReader 的 `acquireNextImage()` 方法中获取图片对象，并将其存储到磁盘上。

## 附录：

### 1. CameraCharacteristics.Key

总结的规则：

1. 中间包含 AE 的表示自动曝光；
2. 中间包含 AF 的表示自动对焦；
3. 中间包含 AWB 的表示自动白平衡。

|字段|说明|
|:-|:-|
public static final Key<int[]>|`COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES`|
public static final Key<int[]>|`CONTROL_AE_AVAILABLE_ANTIBANDING_MODES` 获取相机支持的自动曝光反冲带模式|
public static final Key<int[]>|`CONTROL_AE_AVAILABLE_MODES` 获取相机支持的自动曝光模式|
public static final Key<Range<Integer>>|`CONTROL_AE_COMPENSATION_RANGE` 自动曝光补偿的取值范围|
public static final Key<Boolean>|`CONTROL_AE_LOCK_AVAILABLE` 该相机是否支持 CaptureRequest#CONTROL_AE_LOCK|
public static final Key<int[]>|`CONTROL_AF_AVAILABLE_MODES` 返回相机所支持的自动对焦模式|
public static final Key<int[]>|`CONTROL_AVAILABLE_EFFECTS` 获取相机支持的色彩效果|
public static final Key<int[]>|`CONTROL_AVAILABLE_MODES` 获取相机支持的控制模式`|
public static final Key<int[]>|`CONTROL_AVAILABLE_SCENE_MODES` 获取相机支持的场景模式|
public static final Key<int[]>|`CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES` 获取相机支持的视讯稳定模式|
public static final Key<int[]>|`CONTROL_AWB_AVAILABLE_MODES` 获取相机支持的自动白平衡模式|
public static final Key<Boolean>|`CONTROL_AWB_LOCK_AVAILABLE` 相机是否支持白平衡锁|
public static final Key<Integer>|`CONTROL_MAX_REGIONS_AE`|
public static final Key<Integer>|`CONTROL_MAX_REGIONS_AF`|
public static final Key<Integer>|`CONTROL_MAX_REGIONS_AWB`|
public static final Key<Range<Integer>>|`CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE`|
public static final Key<Boolean>|`DEPTH_DEPTH_IS_EXCLUSIVE`|
public static final Key<int[]>|`DISTORTION_CORRECTION_AVAILABLE_MODES`|
public static final Key<int[]>|`EDGE_AVAILABLE_EDGE_MODES` 获取支持的边缘增强模式|
public static final Key<Boolean>|`FLASH_INFO_AVAILABLE` 相机是否有闪光单元|
public static final Key<int[]>|`HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES`|
public static final Key<Integer>|`INFO_SUPPORTED_HARDWARE_LEVEL`|
public static final Key<String>|`INFO_VERSION` 版本信息字符串`|
public static final Key<Size[]>|`JPEG_AVAILABLE_THUMBNAIL_SIZES` JPEG 支持的尺寸|
public static final Key<float[]>|`LENS_DISTORTION` 相机的矫正系数|
public static final Key<Integer>|`LENS_FACING` 用来获取相机的方向，比如前置相机和后置相机|
public static final Key<float[]>|`LENS_INFO_AVAILABLE_APERTURES`|
public static final Key<float[]>|`LENS_INFO_AVAILABLE_FILTER_DENSITIES`|
public static final Key<float[]>|`LENS_INFO_AVAILABLE_FOCAL_LENGTHS`|
public static final Key<int[]>|`LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`|
public static final Key<Integer>|`LENS_INFO_FOCUS_DISTANCE_CALIBRATION`|
public static final Key<Float>|`LENS_INFO_HYPERFOCAL_DISTANCE`|
public static final Key<Float>|`LENS_INFO_MINIMUM_FOCUS_DISTANCE`|
public static final Key<float[]>|`LENS_INTRINSIC_CALIBRATION`|
public static final Key<Integer>|`LENS_POSE_REFERENCE`|
public static final Key<float[]>|`LENS_POSE_ROTATION` 相机相对于传感器坐标系的方向|
public static final Key<float[]>|`LENS_POSE_TRANSLATION`|
public static final Key<float[]>|`LENS_RADIAL_DISTORTION`|
public static final Key<Integer>|`LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE`|
public static final Key<int[]>|`NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES`|
public static final Key<Integer>|`REPROCESS_MAX_CAPTURE_STALL`|
public static final Key<int[]>|`REQUEST_AVAILABLE_CAPABILITIES`|
public static final Key<Integer>|`REQUEST_MAX_NUM_INPUT_STREAMS`|
public static final Key<Integer>|`REQUEST_MAX_NUM_OUTPUT_PROC`|
public static final Key<Integer>|`REQUEST_MAX_NUM_OUTPUT_PROC_STALLING`|
public static final Key<Integer>|`REQUEST_MAX_NUM_OUTPUT_RAW`|
public static final Key<Integer>|`REQUEST_PARTIAL_RESULT_COUNT`|
public static final Key<Byte>|`REQUEST_PIPELINE_MAX_DEPTH`|
public static final Key<Float>|`SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`|
public static final Key<Integer>|`SCALER_CROPPING_TYPE`|
public static final Key<MandatoryStreamCombination[]>|`SCALER_MANDATORY_STREAM_COMBINATIONS`|
public static final Key<StreamConfigurationMap>|`SCALER_STREAM_CONFIGURATION_MAP`用来获取 StreamConfigurationMap，然后可以从 StreamConfigurationMap 中获取输出流配置信息。可以使用 StreamConfigurationMap 的方法 `getOutputSizes()` 获取指定类型的图片或者类支持的尺寸、格式等信息。|
public static final Key<int[]>|`SENSOR_AVAILABLE_TEST_PATTERN_MODES`|
public static final Key<BlackLevelPattern>|`SENSOR_BLACK_LEVEL_PATTERN`|
public static final Key<ColorSpaceTransform>|`SENSOR_CALIBRATION_TRANSFORM1`|
public static final Key<ColorSpaceTransform>|`SENSOR_CALIBRATION_TRANSFORM2`|
public static final Key<ColorSpaceTransform>|`SENSOR_COLOR_TRANSFORM1`|
public static final Key<ColorSpaceTransform>|`SENSOR_COLOR_TRANSFORM2`|
public static final Key<ColorSpaceTransform>|`SENSOR_FORWARD_MATRIX1`|
public static final Key<ColorSpaceTransform>|`SENSOR_FORWARD_MATRIX2`|
public static final Key<Rect>|`SENSOR_INFO_ACTIVE_ARRAY_SIZE`|
public static final Key<Integer>|`SENSOR_INFO_COLOR_FILTER_ARRANGEMENT`|
public static final Key<Range<Long>>|`SENSOR_INFO_EXPOSURE_TIME_RANGE`|
public static final Key<Boolean>|`SENSOR_INFO_LENS_SHADING_APPLIED`|
public static final Key<Long>|`SENSOR_INFO_MAX_FRAME_DURATION`|
public static final Key<SizeF>|`SENSOR_INFO_PHYSICAL_SIZE`|
public static final Key<Size>|`SENSOR_INFO_PIXEL_ARRAY_SIZE`|
public static final Key<Rect>|`SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE`|
public static final Key<Range<Integer>>|`SENSOR_INFO_SENSITIVITY_RANGE`|
public static final Key<Integer>|`SENSOR_INFO_TIMESTAMP_SOURCE`|
public static final Key<Integer>|`SENSOR_INFO_WHITE_LEVEL`|
public static final Key<Integer>|`SENSOR_MAX_ANALOG_SENSITIVITY`|
public static final Key<Rect[]>|`SENSOR_OPTICAL_BLACK_REGIONS`|
public static final Key<Integer>|`SENSOR_ORIENTATION` 输出的图像要旋转的角度|
public static final Key<Integer>|`SENSOR_REFERENCE_ILLUMINANT1`|
public static final Key<Byte>|`SENSOR_REFERENCE_ILLUMINANT2`|
public static final Key<int[]>|`SHADING_AVAILABLE_MODES`|
public static final Key<int[]>|`STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`|
public static final Key<boolean[]>|`STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES`|
public static final Key<int[]>|`STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES`|
public static final Key<int[]>|`STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES`|
public static final Key<Integer>|`STATISTICS_INFO_MAX_FACE_COUNT`|
public static final Key<Integer>|`SYNC_MAX_LATENCY`|
public static final Key<int[]>|`TONEMAP_AVAILABLE_TONE_MAP_MODES`|
public static final Key<Integer>|`TONEMAP_MAX_CURVE_POINTS`|

### 2. CaptureResult.Key

### 3. CaptureRequest.Key

### 4. TotalCaptureResult.Key

### 5. CamcorderProfile

|常量||
|:-|:-|
|int|`QUALITY_1080P` 质量等级对应1080p（1920 x 1080）分辨率。
int|`QUALITY_2160P` 质量等级对应2160p（3840 x 2160）分辨率。
int|`QUALITY_480P` 质量等级对应480p（720 x 480）分辨率。
int|`QUALITY_720P` 质量等级对应720p（1280 x 720）分辨率。
int|`QUALITY_CIF` 质量等级对应于cif（352 x 288）分辨率。
int|`QUALITY_HIGH` 质量等级对应于最高可用分辨率。
int|`QUALITY_HIGH_SPEED_1080P` 速（>=100fps）质量等级，对应1080p（1920 x 1080或1920x1088）分辨率。
int|`QUALITY_HIGH_SPEED_2160P` 高速（>=100fps）质量等级，对应2160p（3840 x 2160）分辨率。
int|`QUALITY_HIGH_SPEED_480P` 高速（>=100fps）质量等级，对应480p（720 x 480）分辨率。
int|`QUALITY_HIGH_SPEED_720P` 高速（>=100fps）质量等级，对应720p（1280 x 720）分辨率。
int|`QUALITY_HIGH_SPEED_HIGH` 高速（>=100fps）质量等级，对应于最高可用分辨率。
int|`QUALITY_HIGH_SPEED_LOW` 高速（>=100fps）质量等级，对应于最低可用分辨率。
int|`QUALITY_LOW` 质量等级对应于最低可用分辨率。
int|`QUALITY_QCIF` 质量等级对应qcif（176 x 144）分辨率。
int|`QUALITY_QVGA` 质量等级对应QVGA（320x240）分辨率。
int|`QUALITY_TIME_LAPSE_1080P` 时间流逝质量等级对应于1080p（1920 x 1088）分辨率。
int|`QUALITY_TIME_LAPSE_2160P` 时间流逝质量等级对应2160p（3840 x 2160）分辨率。
int|`QUALITY_TIME_LAPSE_480P` 时间流逝质量等级对应480p（720 x 480）分辨率。
int|`QUALITY_TIME_LAPSE_720P` 时间流逝质量等级对应720p（1280 x 720）分辨率。
int|`QUALITY_TIME_LAPSE_CIF` 时间推移质量等级对应于cif（352 x 288）分辨率。
int|`QUALITY_TIME_LAPSE_HIGH` 与最高可用分辨率对应的时间推移质量等级。
int|`QUALITY_TIME_LAPSE_LOW` 与最低可用分辨率对应的时间推移质量等级。
int|`QUALITY_TIME_LAPSE_QCIF` 时间推移质量等级对应于qcif（176 x 144）分辨率。
int|`QUALITY_TIME_LAPSE_QVGA` 时间推移质量等级对应于QVGA（320 x 240）分辨率。

|字段||
|:-|:-|
public int|`audioBitRate` 目标音频输出比特率，以每秒位数为单位
public int|`audioChannels` 用于音轨的音频通道数
public int|`audioCodec` 音频编码器用于音轨。
public int|`audioSampleRate` 用于音轨的音频采样率
public int|`duration` 会话终止前的默认录制持续时间（秒）。
public int|`fileFormat` 摄像机配置文件的文件输出格式
public int|`quality` 摄像机配置文件的质量等级
public int|`videoBitRate` 目标视频输出比特率，以每秒位数为单位。如果应用程序在MediaRecorder#setProfile不指定任何其他MediaRecorder编码参数的情况下`配置视频录制，则这是目标录制的视频输出比特率。
public int|`videoCodec` 视频编码器用于视频轨道
public int|`videoFrameHeight` 目标视频帧高度（以像素为单位）
public int|`videoFrameRate` 目标视频帧速率，以每秒帧数为单位。
public int|`videoFrameWidth` 目标视频帧宽度（以像素为单位）

公共方法||
|:-|:-|
static CamcorderProfile|`get(int quality)` 以给定的质量级别返回设备上第一台后置摄像头的摄像机配置文件。
static CamcorderProfile|`get(int cameraId, int quality)` 以给定的质量级别返回给定摄像机的摄像机配置文件。
static boolean|`hasProfile(int cameraId, int quality)` 如果给定质量级别的给定摄像机存在摄像机配置文件，则返回true。
static boolean|`hasProfile(int quality)` 如果给定质量级别的第一台后置摄像机存在摄像机配置文件，则返回true。


