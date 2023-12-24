package com.chad.library.adapter.base.loadState

import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.fullspan.FullSpanAdapterType

/**
 * Load state adapter
 * 加载状态的夫类，"加载更多"、"向上加载"都继承于此
 *
 */
abstract class LoadStateAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>(),
    FullSpanAdapterType {
    /**
     * Changing this property will immediately notify the Adapter to change the item it's
     * presenting.
     * [LoadState.None] is the initial state.
     *
     * 要在适配器中显示的 LoadState。更改此属性将立即通知适配器更改 item 的样式。
     * [LoadState.None] 为初始状态。
     */
    var loadState: LoadState = LoadState.None
        set(loadState) {
            if (field != loadState) {
                val oldItem = displayLoadStateAsItem(field)
                val newItem = displayLoadStateAsItem(loadState)

                if (oldItem && !newItem) {
                    notifyItemRemoved(0)
                } else if (newItem && !oldItem) {
                    notifyItemInserted(0)
                } else if (oldItem && newItem) {
                    notifyItemChanged(0)
                }
                field = loadState
            }
        }

    /**
     * Is it loading.
     *
     * 是否加载中
     */
    val isLoading: Boolean
        get() {
            return loadState == LoadState.Loading
        }

    var recyclerView: RecyclerView? = null
        private set

    var onAllowLoadingListener: OnAllowLoadingListener? = null
        private set

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return onCreateViewHolder(parent, loadState)
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {
        onBindViewHolder(holder, loadState)
    }

    final override fun getItemViewType(position: Int): Int = getStateViewType(loadState)

    final override fun getItemCount(): Int = if (displayLoadStateAsItem(loadState)) 1 else 0

    @CallSuper
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    @CallSuper
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    /**
     * Called to create a ViewHolder for the given LoadState.
     *
     * @param parent The ViewGroup into which the new View will be added after it is bound to
     *               an adapter position.
     * @param loadState The LoadState to be initially presented by the new ViewHolder.
     *
     * @see [getItemViewType]
     * @see [displayLoadStateAsItem]
     */
    abstract fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): VH

    /**
     * Called to bind the passed LoadState to the ViewHolder.
     *
     * @param loadState LoadState to display.
     *
     * @see [getItemViewType]
     * @see [displayLoadStateAsItem]
     */
    abstract fun onBindViewHolder(holder: VH, loadState: LoadState)

    /**
     * Override this method to use different view types per LoadState.
     *
     * By default, this LoadStateAdapter only uses a single view type.
     */
    open fun getStateViewType(loadState: LoadState): Int = 0

    /**
     * Returns true if the LoadState should be displayed as a list item when active.
     *
     * By default, [LoadState.Loading] and [LoadState.Error] present as list items, others do not.
     *
     *
     * 如果 LoadState 在活动时需要显示item，则返回 true。
     * 默认情况下，[LoadState.Loading] 和 [LoadState.Error] 将会显示，其他则不显示。
     */
    open fun displayLoadStateAsItem(loadState: LoadState): Boolean {
        return loadState is LoadState.Loading || loadState is LoadState.Error
    }

    fun setOnAllowLoadingListener(listener: OnAllowLoadingListener?) = apply {
        this.onAllowLoadingListener = listener
    }

    interface OnAllowLoadingListener {
        /**
         * Whether to allow loading.
         * 是否允许进行加载
         */
        fun isAllowLoading(): Boolean = true
    }
}
