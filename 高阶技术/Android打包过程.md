# Android 打包过程

![打包过程](https://upload-images.jianshu.io/upload_images/1441907-8a2c24bbb71c2cbf.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/536/format/webp)

## 编译资源

aapt 类目录：`base\tools\aapt` 和 `base\tools\aapt2`

aapt 工具目录：`sdk/build-tools/27.0.3/aapt.exe` 和 `sdk/build-tools/27.0.3/aapt2.exe`

## AIDL

工具目录：`sdk/build-tools/27.0.3/aidl.exe`

## DX

工具目录：`sdk/build-tools/27.0.3/lib/dx.jar`
工具目录：`sdk/build-tools/27.0.3/dx.bat`

## 签名

工具目录：`sdk/build-tools/27.0.3/lib/apksigner.jar`
工具目录：`sdk/build-tools/27.0.3/apksigner.bat`

遍历 apk 中所有文件，对非文件夹非签名文件的文件逐个生成 SHA1 数字签名信息，再 base64 编码 然后再写入 MANIFEST.MF 文件中

SHA1 生成的摘要信息，如果你修改了某个文件，apk 安装校验时，取到的该文件的摘要与 MANIFEST.MF 中对应的摘要不同，则安装不成功

接下来对之前生成的 manifest 使用 SHA1withRSA 算法， 用私钥签名，writeSignatureFile 这个函数，最后生成 CERT.SF 文件

https://blog.csdn.net/tencent_bugly/article/details/51424209

## 对齐

它对apk中未压缩的数据进行4字节对齐，对齐后就可以使用mmap函数读取文件，可以像读取内存一样对普通文件进行操作。如果没有4字节对齐，就必须显式的读取，这样比较缓慢并且会耗费额外的内存。

工具位于 `sdk\build-tools\27.0.3`

https://www.jianshu.com/p/7c288a17cda8