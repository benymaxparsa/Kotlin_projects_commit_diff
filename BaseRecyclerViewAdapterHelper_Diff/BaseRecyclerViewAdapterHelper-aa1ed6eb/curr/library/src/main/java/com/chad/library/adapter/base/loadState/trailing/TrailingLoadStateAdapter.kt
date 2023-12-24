package com.chad.library.adapter.base.loadState.trailing

import androidx.annotation.CallSuper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.chad.library.adapter.base.loadState.LoadState
import com.chad.library.adapter.base.loadState.LoadStateAdapter

/**
 * Tail load more parent class Adapter.
 * 尾部加载更多的父类 Adapter
 *
 * Custom layout: You can modify the layout by extends this class and customizing [RecyclerView.ViewHolder].
 * 自定义布局：可以通过继承此类，并自定义[RecyclerView.ViewHolder]来修改布局
 */
abstract class TrailingLoadStateAdapter<VH : RecyclerView.ViewHolder> : LoadStateAdapter<VH>() {

    /**
     * 加载更多的监听事件
     */
    var onTrailingListener: OnTrailingListener? = null
        private set

    /**
     * 所有数据加载完毕后，是否显示"加载结束"
     */
    var isLoadEndDisplay: Boolean = true

    /**
     * 是否打开自动加载更多
     */
    var isAutoLoadMore = true

    /**
     * 当自动加载更多开启，并且数据不满一屏时，是否关闭自动加载更多。默认为 false
     */
    var isDisableLoadMoreIfNotFullPage = false

    /**
     * 预加载，距离尾部 item 的个数
     */
    var preloadSize = 0

    /**
     * 不满一屏时，是否可以继续加载的标记位
     */
    private var mNextLoadEnable: Boolean = true

    override fun displayLoadStateAsItem(loadState: LoadState): Boolean {
        return super.displayLoadStateAsItem(loadState)
                || (!isAutoLoadMore && (loadState is LoadState.NotLoading && !loadState.endOfPaginationReached)) // 加载完成的状态，并且没有打开自动加载更多，需要显示
                || (isLoadEndDisplay && (loadState is LoadState.NotLoading && loadState.endOfPaginationReached)) // 加载彻底结束，不会再有分页数据的情况，并且需要显示结束后的item
    }

    @CallSuper
    override fun onViewAttachedToWindow(holder: VH) {
        loadAction()
    }

    /**
     * 执行加载的操作
     */
    private fun loadAction() {
        if (!isAutoLoadMore || onAllowLoadingListener?.isAllowLoading() == false) {
            // 不允许进行加载更多（例如：正在进行下拉刷新）
            return
        }

        if (!mNextLoadEnable) {
            return
        }

        if (loadState is LoadState.NotLoading && !loadState.endOfPaginationReached) {
            val recyclerView = recyclerView ?: return

            if (recyclerView.isComputingLayout) {
                // 如果 RecyclerView 当前正在计算布局，则延迟执行，避免崩溃
                recyclerView.post {
                    invokeLoadMore()
                }
                return
            }

            invokeLoadMore()
        }
    }

    internal fun checkPreload(listSize: Int, currentPosition: Int) {
        if (currentPosition > listSize - 1) return

        if (listSize - currentPosition - 1 <= preloadSize) {
            loadAction()
        }
    }

    fun invokeLoadMore() {
        loadState = LoadState.Loading
        onTrailingListener?.onLoad()
    }

    fun invokeFailRetry() {
        loadState = LoadState.Loading
        onTrailingListener?.onFailRetry()
    }

    fun checkDisableLoadMoreIfNotFullPage() {
        if (!isDisableLoadMoreIfNotFullPage) {
            return
        }
        // 先把标记位设置为false
        mNextLoadEnable = false
        val recyclerView = this.recyclerView ?: return
        val manager = recyclerView.layoutManager ?: return
        if (manager is LinearLayoutManager) {
            recyclerView.post {
                if (isFullScreen(manager)) {
                    mNextLoadEnable = true
                }
            }
        } else if (manager is StaggeredGridLayoutManager) {
            recyclerView.post {
                // TODO
//                val positions = IntArray(manager.spanCount)
//                manager.findLastCompletelyVisibleItemPositions(positions)
//                val pos = getTheBiggestNumber(positions) + 1
//                if (pos != baseQuickAdapter.itemCount) {
//                    mNextLoadEnable = true
//                }
            }
        }
    }

    private fun isFullScreen(llm: LinearLayoutManager): Boolean {
        val adapter = recyclerView?.adapter ?: return true

        return (llm.findLastCompletelyVisibleItemPosition() + 1) != adapter.itemCount ||
                llm.findFirstCompletelyVisibleItemPosition() != 0
    }

    fun setOnLoadMoreListener(listener: OnTrailingListener?) = apply {
        this.onTrailingListener = listener
    }

    interface OnTrailingListener {

        /**
         * "加载更多"执行逻辑
         */
        fun onLoad()

        /**
         * 失败的情况下，点击重试执行的逻辑
         */
        fun onFailRetry()
    }
}