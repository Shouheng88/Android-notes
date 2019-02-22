

## 相机

- [ ] Android 中开启摄像头的主要步骤


## 压缩

深度研究：

1. SurefaceView, TextureView, Camera
2. RecyclerView
3. Adapter + Fragment

热修补+插件化（组件化）

PMW WMS AMW 相关的东西



## 项目相关

以上的深度研究 + 屏幕适配方式 + WorkManager 的研究






实际相机拍照的时候是先把照片写到磁盘上面然后在从磁盘上面加载到内存的时候使用一个采样率来采样。从最终的效果来看，设置采样率起到了压缩的作用，但是它只是改变了加载的图片的比率。真正起到压缩作用的是把加载到内存之后的 Bitmap 再次写入到磁盘上面并替换原始的文件。所以，相机采用多大的预览图和输出的图片的尺寸跟最终得到的图片的尺寸和大小没有关系——拍摄的照片写入到磁盘上面之后对其进行压缩。

Q：照片太大的话压缩和写入磁盘的效率可能降低，但是这会有多少的性能损失呢？相机预览和输出的时候，寻找一个不是太大的比例？

Q：图片压缩的尺寸压缩的问题，难道除了设置成 2 的比例，就没有其他的办法了吗？

```java
/**
邻近采样：采用一个 2 的倍数，
1. CompressFormat format：压缩格式，它有 JPEG、PNG、WEBP 三种选择，JPEG 是有损压缩，PNG 是无损压缩，压缩后的图像大小不会变化（也就是没有压缩效果），WEBP 是 Google 推出的图像格式，它相比 JPEG 会节省 30% 左右的空间，处于兼容性和节省空间的综合考虑，我们一般会选择 JPEG。
2. int quality：0~100 可选，数值越大，质量越高，图像越大。
3. OutputStream stream：压缩后图像的输出流。
*/
BitmapFactory.Options options = new BitmapFactory.Options();
options.inSampleSize = 1; // 设置加载图片时的采样率
Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.blue_red, options);
bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream); // 压缩，然后使用 stream 写出数据
bitmap.recycle();

/**
双线性采样：Bitmap.createBitmap() 的几个参数的意义，
1. Bitmap source：源图像
2. int x：目标图像第一个像素的 x 坐标
3. int y：目标图像第一个像素的 y 坐标
4. int width：目标图像的宽度（像素点个数）
5. int height：目标图像的高度（像素点个数）
6. Matrix m：变换矩阵
7. boolean filter：是否开启过滤
*/
Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.blue_red);
Matrix matrix = new Matrix();
matrix.setScale(0.5f, 0.5f);
Bitmap sclaedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth()/2, bitmap.getHeight()/2, matrix, true);
ImageUtils.save(bitmap, savePath, Bitmap.CompressFormat.PNG);
```

但是第二种方式存在一个问题，因为它在进行压缩之前需要将图片全部加载到内存中，如果图片比较大，那么可能会把内存撑爆，导致 OOM. 

所以，我们可以考虑结合两种方式拉

Q：如何根据相机支持的图像的质量选择一个合适的尺寸？即使相同的尺寸和质量的图片，不同分辨率的手机拍摄出的效果还是不同的？如何得出图片的质量的等级？不同分辨率的手机拍摄出的照片影响的是什么的效果？

Q：图片的 Bitmap 的计算规则与最终上次的文件的计算规则一样吗？区别是什么？



其他：视频压缩，视频录制


# Android 性能优化-相机优化

场景，人工智能识别图片，对图片质量要求较高，同时为了加快图片上传的速度，需要对图片的大小进行控制，也就是既要保证图片在的质量又要控制图片的体积。照片是由自定义相机拍摄完成的，在拍摄的时候相关参数的选择，相机各种功能的完善等。

所以关注的地方在于，第一是相机，封装一个功能完善的相机库，可以处理常见的问题，同时对 Camera1 和 Camera2 进行兼容。第二是图片的压缩，保证图片的质量，并控制图片的大小。

相机：

相机要解决的几个问题，

1. 使用 Camera1 还是 Camera2 的问题
2. TextureView 还是 SurfaceView 的问题
3. 相机的预览尺寸、输出图片的尺寸和拍摄视频的尺寸的计算
4. 手势缩放
5. 对焦（自动对焦、外部调整对焦）
6. 相机实时预览时，提供对外接口，可以对图片实时进行处理

压缩：

1. 使用邻近采样将图片加载到内存中（内存防爆）
2. 使用质量压缩和双线性采样控制图片的尺寸和质量

遗留的问题，不同分辨率的相机拍摄出的相片的质量不一样，在图片尺寸相同的情况下，相机硬件会影响图片的什么呢？


