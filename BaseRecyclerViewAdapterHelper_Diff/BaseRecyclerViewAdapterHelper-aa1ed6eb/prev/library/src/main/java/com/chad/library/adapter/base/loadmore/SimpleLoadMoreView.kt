package com.chad.library.adapter.base.loadmore

import android.view.View
import android.view.ViewGroup
import com.chad.library.R
import com.chad.library.adapter.base.viewholder.QuickViewHolder
import com.chad.library.adapter.base.util.getItemView

class SimpleLoadMoreView : BaseLoadMoreView() {

    override fun getRootView(parent: ViewGroup): View =
            parent.getItemView(R.layout.brvah_quick_view_load_more)

    override fun getLoadingView(holder: QuickViewHolder): View =
            holder.getView(R.id.load_more_loading_view)

    override fun getLoadComplete(holder: QuickViewHolder): View =
            holder.getView(R.id.load_more_load_complete_view)

    override fun getLoadEndView(holder: QuickViewHolder): View =
            holder.getView(R.id.load_more_load_end_view)

    override fun getLoadFailView(holder: QuickViewHolder): View =
            holder.getView(R.id.load_more_load_fail_view)
}