# Glide 主流程源码分析（4.x）

Glide 是Android 端的图片加载框架，

使用的时候需要在代码中加入下面的依赖。一般添加第一个依赖就可以了，如果需要使用 Glide 的注解，那么还要加入第二个来在项目中启用注解处理：

    dependencies {
        compile 'com.github.bumptech.glide:glide:4.8.0'
        annotationProcessor 'com.github.bumptech.glide:compiler:4.8.0'
    }

然后，一个最基本的使用示例：

    Glide.with(fragment).load(myUrl).into(imageView);

接下来我们来分析这个过程究竟发生了什么。





