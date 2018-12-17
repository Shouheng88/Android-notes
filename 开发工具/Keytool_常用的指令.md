# Keytool 常用的指令合集

### 获取 APK 的签名信息

方式 1：在命令行中输入下面的命令，即可获取 SHA1 签名信息：

    keytool -printcert -file C:\META-INF\CERT.RSA
    
这里的 `C:\META-INF\CERT.RSA` 是从 APK 包中解压出来的 `RSA` 文件的路径。

方式 2：或者使用下面的命令也可以达到相同的目的，并且不用对 APK 进行解压：

    keytool -printcert -jarfile example-release-v2.apk



