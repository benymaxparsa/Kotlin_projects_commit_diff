package com.chad.library.adapter.base

import android.content.Context
import android.util.SparseArray
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * MultiItemType layout.
 * 多类型布局
 *
 */
abstract class BaseMultiItemQuickAdapter<T>(items: MutableList<T> = mutableListOf<T>()) :
    BaseQuickAdapter<T, RecyclerView.ViewHolder>(items) {

    private val typeViewHolders = SparseArray<OnMultiItemAdapterListener<T, RecyclerView.ViewHolder>>(1)
    private val viewHoldersClass =
        HashMap<Class<*>, OnMultiItemAdapterListener<T, RecyclerView.ViewHolder>>(1)

    private var onItemViewTypeListener: OnItemViewTypeListener<T>? = null

    override fun onCreateViewHolder(
        context: Context, parent: ViewGroup, viewType: Int
    ): RecyclerView.ViewHolder {
        val listener = typeViewHolders.get(viewType)
            ?: throw IllegalArgumentException("ViewType: $viewType not found onViewHolderListener，please use addItemType() first!")

        return listener.onCreate(parent.context, parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, item: T) {
//        for ((clazz, listener) in viewHoldersClass) {
//            if (holder::class.java.isAssignableFrom(clazz)) {
//                listener.onBind(holder, position, item)
//                return
//            }
//        }
        viewHoldersClass[holder::class.java]?.onBind(holder, position, item)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder, position: Int, item: T, payloads: List<Any>
    ) {
        if (payloads.isEmpty()) {
            viewHoldersClass[holder::class.java]?.onBind(holder, position, item)
            return
        }
        viewHoldersClass[holder::class.java]?.onBind(holder, position, item, payloads)
    }

    /**
     * Call this function to add multiTypeItems.
     * 调用此方法，设置多布局
     * @param type Int
     * @param listener Int
     */
    inline fun <reified V : RecyclerView.ViewHolder> addItemType(
        type: Int, listener: OnMultiItemAdapterListener<T, V>
    ) = addItemType(type, V::class.java, listener)

    fun <V : RecyclerView.ViewHolder> addItemType(
        type: Int, holderClazz: Class<V>, listener: OnMultiItemAdapterListener<T, V>
    ) = apply {
        typeViewHolders.put(type, listener as OnMultiItemAdapterListener<T, RecyclerView.ViewHolder>)
        viewHoldersClass[holderClazz] = listener
    }

    fun onItemViewType(listener: OnItemViewTypeListener<T>?) = apply {
        this.onItemViewTypeListener = listener
    }

    override fun getItemViewType(position: Int, list: List<T>): Int {
        return onItemViewTypeListener?.onItemViewType(position, list)
            ?: super.getItemViewType(position, list)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        viewHoldersClass[holder::class.java]?.onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        viewHoldersClass[holder::class.java]?.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        viewHoldersClass[holder::class.java]?.onViewRecycled(holder)
    }

    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        return viewHoldersClass[holder::class.java]?.onFailedToRecycleView(holder) ?: super.onFailedToRecycleView(holder)
    }

    override fun isFullSpanItem(itemType: Int): Boolean {
        return super.isFullSpanItem(itemType) ||
                (typeViewHolders.get(itemType)?.isFullSpanItem(itemType) == true)
    }


    interface OnMultiItemAdapterListener<T, V : RecyclerView.ViewHolder> {
        fun onCreate(context: Context, parent: ViewGroup, viewType: Int): V

        fun onBind(holder: V, position: Int, item: T)

        fun onBind(holder: V, position: Int, item: T, payloads: List<Any>) {
            onBind(holder, position, item)
        }

        fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {}

        fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {}

        fun onViewRecycled(holder: RecyclerView.ViewHolder) {}

        fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean = false

        fun isFullSpanItem(itemType: Int): Boolean {
            return false
        }
    }

    fun interface OnItemViewTypeListener<T> {
        fun onItemViewType(position: Int, list: List<T>): Int
    }
}