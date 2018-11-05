# Glide源码学习

在学习Glide的源码之前还是先来看一下Glide加载图片时候的一般使用方式，然后我们根据它的使用方式作为研究的起点：

    Glide.with(getContext())
           .load(FileHelper.getThumbnailUri(getContext(), attachment))
           .asBitmap()
           .diskCacheStrategy(DiskCacheStrategy.SOURCE)
           .thumbnail(0.5f)
           .transform(new RotateTransformation(getContext(), 0, false))
           .animate(R.anim.fade_in_support)
           .into(photoView);





