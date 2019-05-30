##  RV 的各种效果实现

### 触摸

```kotlin
// 定义 ItemTouchHelper.Callback
class SimpleItemTouchCallback<T, K : BaseViewHolder>(private var adapter: SimpleTouchAdapter<T, K>) : ItemTouchHelper.Callback() {

    // 这里指定长按和拖拽允许的方向
    override fun getMovementFlags(p0: RecyclerView, p1: RecyclerView.ViewHolder): Int {
        val upFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        return makeMovementFlags(upFlags, swipeFlags)
    }

    // 长按拖拽的时候会回调这个方法
    override fun onMove(p0: RecyclerView, p1: RecyclerView.ViewHolder, p2: RecyclerView.ViewHolder): Boolean {
        adapter.onItemMove(p1.adapterPosition, p2.adapterPosition)
        return true
    }

    // 非长按状态拖拽的时候回调这个方法
    override fun onSwiped(p0: RecyclerView.ViewHolder, p1: Int) {
        adapter.onSwiped(p0.adapterPosition, p1)
    }
}

// 一个接口，用来回调通知 Adapter
abstract class SimpleTouchAdapter<T, K : BaseViewHolder>(layoutResId: Int) : BaseQuickAdapter<T, K>(layoutResId) {

    abstract fun onSwiped(position: Int, direction: Int)

    abstract fun onItemMove(from: Int, to: Int)
}

// Adapter
class ColorfulAdapter : SimpleTouchAdapter<ColorModel, BaseViewHolder>(R.layout.item_colorful) {

    override fun onSwiped(position: Int, direction: Int) {
        data.removeAt(position)
        notifyItemRemoved(position)
    }

    // 本质上就是数据位置交换的逻辑，上面的触摸事件不会真正地导致数据交换，因此我们在回调接口中自己实现这些逻辑
    override fun onItemMove(from: Int, to: Int) {
        if (from < to) {
            for (i in from until to) {
                Collections.swap(data, i, i + 1)
            }
        } else {
            for (i in to until from) {
                Collections.swap(data, i, i + 1)
            }
        }
        notifyItemMoved(from, to)
    }

    override fun convert(helper: BaseViewHolder?, item: ColorModel?) {
        helper?.setBackgroundColor(R.id.ll, item?.color!!)
        helper?.setText(R.id.tv, item?.name)
    }
}
```

最终的应用方式：

```kotlin
    // 构建 Adapter
    val adapter = ColorfulAdapter()
    adapter.setNewData(colorModels)
    binding.rv.adapter = adapter
    // 构建一个 ItemTouchHelper，并将其关联到 RV
    callback = SimpleItemTouchCallback(true, true, adapter)
    val touchHelper = ItemTouchHelper(callback)
    touchHelper.attachToRecyclerView(binding.rv)
```










