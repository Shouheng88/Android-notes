# Tinker 热补丁的源码分析

Tinker是微信官方的Android热补丁解决方案，它支持动态下发代码、So库以及资源，让应用能够在不需要重新安装的情况下实现更新。当然，你也可以使用Tinker来更新你的插件。

Github https://github.com/Tencent/tinker

它主要包括以下几个部分：

1. gradle编译插件: `tinker-patch-gradle-plugin`
2. 核心sdk库: `tinker-android-lib`
3. 非gradle编译用户的命令行版本: `tinker-patch-cli.jar`



