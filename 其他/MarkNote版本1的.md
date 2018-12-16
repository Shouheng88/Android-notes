# 马克笔记—Android 端开源的 Markdown 笔记应用

![App 导引](https://github.com/Shouheng88/MarkNote/blob/master/resources/images/app.png?raw=true)

> 马克笔记是运行在Android设备上面的一款开源的Markdown笔记，它的功能开发得已经比较完善，已经能够满足大部分用户的需求。现在将其开源到Github上面，用来交流和学习。当然，更希望你能够参与到项目的开发当中，帮助马克笔记变得更加有用。

## 1、关于马克笔记

马克笔记是一款开源的Markdown笔记应用，它的界面设计采用了Google最新的Material Design风格。该笔记现在的功能已经比较完善，能够满足用户大多数场景的需求。开源该软件的目的是希望与更多的人交流和学习，同时也希望能够有人参与到项目的开发中，一起帮助马克笔记，让它变得更加有用。

你可以通过加入[Google+社区](https://plus.google.com/u/1/communities/102252970668657211916)来关注该软件开发的最新动态，并且可以参与Beta测试。

马克笔记现在已经发布到了[酷安网](https://www.coolapk.com/apk/178276)上面，也欢迎你下载和使用该软件。另外，笔者还开发了一款清单应用[多功能清单](https://www.coolapk.com/apk/185660)，感兴趣的同学也可以了解一下。

## 2、应用展示图

<a href="#app">这里</a>是该应用的一些截图通过Photoshop调整之后得到的展示图，通过展示图，你大概可以了解一下该软件的主要功能和开发状态。在接下来的行文中，我会向你更详细地介绍它使用到的一些技术以及现在开发完成的一些功能和特性。

## 3、功能和特性

我把该软件当前已经支持的功能列了一个清单：

|编号|功能|
|:-:|:-|
|1|基本的**添加、修改、归档、放进垃圾箱、彻底删除**操作|
|2|基本的Markdown语法，外加**MathJax**等高级特性|
|3|特色的**时间线**功能，通过类似于AOP的操作记录用户的操作信息|
|4|多种形式的媒体数据，包括**文件、视频、音频、图片、手写和位置信息**等|
|5|**多主题**，支持**夜间主题**，并且有多种可选的**主题色和强调色**|
|6|多彩的**图表**用于统计用户的数据信息|
|7|三种形式的**桌面小控件**，并且可以为每个笔记添加快捷方式|
|8|允许你为笔记指定多个多彩的标签|
|9|使用“树结构”模拟文件夹操作，支持**多层文件夹**，并可以进行层级的搜索|
|10|允许将笔记**导出为PDF、TXT、MD格式的文本、HTML和图片**|
|11|使用**应用独立锁**，加强数据安全|
|12|允许用户**备份数据到外部存储空间和OneDrive**|
|13|图片**自动压缩**，节省本地的数据存储空间|

将来希望开发和完善的功能:

|编号|功能描述|
|:-:|:-|
|1|数据同步，本地的文件管理容易导致多平台的不一致，增加同步服务，能够实现多平台操作|
|2|文件服务器，用于获取图片和文件的链接|
|3|富文本编辑，即时的编辑预览|
|4|允许添加闹钟，并且复选框可以编辑|
|5|添加地图来展示用户的位置信息的变更|

你可以从[更新日志](app/src/main/res/raw/changelog.xml)中获取到软件的更新信息。

## 4、依赖和用到的一些技术

马克笔记用到了MVVM的设计模式，还用到了DataBinding等一系列技术。下面的表格中列出了用到的具体的依赖和简要的描述。在此，还要感谢这些开源项目的作者：

|编号|依赖|描述|
|:-:|:-|:-|
|1|[arch.lifecycle]()|使用ViewModel+LiveData实现Model和View的解耦|
|2|[Stetho](https://github.com/facebook/stetho)|Facebook开源的安卓调试框架|
|3|[Fabric]()|错误跟踪，用户数据收集|
|4|[RxBinding](https://github.com/JakeWharton/RxBinding)||
|5|[RxJava](https://github.com/ReactiveX/RxJava)||
|6|[RxAndroid](https://github.com/ReactiveX/RxAndroid)||
|7|[OkHttp](https://github.com/square/okhttp)||
|8|[Retrofit](https://github.com/square/retrofit)||
|9|[Glide](https://github.com/bumptech/glide)||
|10|[BRVAH](https://github.com/CymChad/BaseRecyclerViewAdapterHelper)|非常好用的Recycler适配器|
|11|[Gson](https://github.com/google/gson)||
|12|[Joda-Time](https://github.com/JodaOrg/joda-time)|Java时间库|
|13|[Apache IO](http://commons.apache.org/io/)|文件操作库|
|14|[Material dialogs](https://github.com/afollestad/material-dialogs)||
|15|[PhotoView](https://github.com/chrisbanes/PhotoView)||
|16|[Hello charts](https://github.com/lecho/hellocharts-android)||
|17|[FloatingActionButton](https://github.com/Clans/FloatingActionButton)||
|18|[HoloColorPicker](https://github.com/LarsWerkman/HoloColorPicker)||
|19|[CircleImageView](https://github.com/hdodenhof/CircleImageView)||
|20|[Changeloglib](https://github.com/gabrielemariotti/changeloglib)|日志信息|
|21|[PinLockView](https://github.com/aritraroy/PinLockView)|锁控件|
|22|[BottomSheet](https://github.com/Kennyc1012/BottomSheet)|底部弹出的对话框|
|23|[Luban](https://github.com/Curzibn/Luban)|图片压缩|
|24|[Flexmark](https://github.com/vsch/flexmark-java)|基于Java的Markdown文本解析|
|25|[PrettyTime](https://github.com/ocpsoft/prettytime)|时间格式美化|


特别需要说明的一点是，马克笔记是在开发了一段时间之后重新引入的ViewModel，因为作者本人水平有限，或者对ViewModel理解不够深入，设计难免有不足的地方，还请批评指正。

### 数据库操作

对于数据库部分，笔者自己设计了一套数据的访问逻辑，这里使用到了模板和单例等设计模式。它的好处在于，当你想要向程序中添加一个数据库实体的时候，只需要很少的配置即可，可以省去很多的样板代码。而且，由于该项目的一些特殊需求，比如要记录统计信息等，所以就自己设计了一下。当然，可能性能上仍然有许多值得提升的地方，但笔者认为仍不失为一个简单的学习材料。

### Markdown解析

对于Markdown解析，可以使用js在webview里面解析，也可以像本项目一样在程序种用java进行解析。笔者认为使用Flexmark在java种解析的好处是更方便地对解析的功能进行拓展。如该软件中的MathJax的解析就是在Flexmark的基础上进行的拓展。

## 5、参与项目

正如一开始提及的那样，马克笔记仍然有许多不足，我希望可以有更多的人帮助马克笔记继续完善它的功能。当然，这并不勉强。如果你希望对该项目贡献代码，你可以fork该项目，并向该项目提交请求。你可以在[waffle.io](https://waffle.io/Shouheng88/NotePal)上面跟踪issue的开发状态。或者，你发现了该软件中存在的一些问题，你可以在issue中向开发者报告。如果有其他的需求，可以直接通过[邮箱](mailto:shouheng2015@gmail.com)邮件开发者。

## 6、项目地址

因为这篇文章是从Github的Readme文件中拷贝出来的，所以忘记加上Github地址了，抱歉。现在补上：[Github](https://github.com/Shouheng88/MarkNote)