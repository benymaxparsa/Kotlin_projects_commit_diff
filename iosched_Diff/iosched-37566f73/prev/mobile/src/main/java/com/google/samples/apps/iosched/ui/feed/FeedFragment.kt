/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.common.collect.ImmutableMap
import com.google.samples.apps.iosched.databinding.FragmentFeedBinding
import com.google.samples.apps.iosched.model.Moment
import com.google.samples.apps.iosched.model.SessionId
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper
import com.google.samples.apps.iosched.shared.result.EventObserver
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider
import com.google.samples.apps.iosched.shared.util.toEpochMilli
import com.google.samples.apps.iosched.shared.util.viewModelProvider
import com.google.samples.apps.iosched.ui.MainNavigationFragment
import com.google.samples.apps.iosched.ui.feed.FeedFragmentDirections.Companion.toMap
import com.google.samples.apps.iosched.ui.feed.FeedFragmentDirections.Companion.toSchedule
import com.google.samples.apps.iosched.ui.feed.FeedFragmentDirections.Companion.toSessionDetail
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager
import com.google.samples.apps.iosched.ui.setUpSnackbar
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment
import com.google.samples.apps.iosched.ui.signin.setupProfileMenuItem
import com.google.samples.apps.iosched.util.doOnApplyWindowInsets
import com.google.samples.apps.iosched.util.openWebsiteUrl
import timber.log.Timber
import javax.inject.Inject

class FeedFragment : MainNavigationFragment() {

    companion object {
        private const val DIALOG_NEED_TO_SIGN_IN = "dialog_need_to_sign_in"
        private const val BUNDLE_KEY_SESSIONS_LAYOUT_MANAGER_STATE = "sessions_layout_manager"
    }

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var snackbarMessageManager: SnackbarMessageManager

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    private lateinit var model: FeedViewModel
    private lateinit var binding: FragmentFeedBinding
    private var adapter: FeedAdapter? = null
    private lateinit var sessionsViewBinder: FeedSessionsViewBinder

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        model = viewModelProvider(viewModelFactory)

        binding = FragmentFeedBinding.inflate(
            inflater, container, false
        ).apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = model
        }

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(
            BUNDLE_KEY_SESSIONS_LAYOUT_MANAGER_STATE,
            sessionsViewBinder.recyclerViewManagerState
        )
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsHelper.sendScreenView("Home", requireActivity())

        binding.toolbar.setupProfileMenuItem(activityViewModelProvider(viewModelFactory), this)

        binding.root.doOnApplyWindowInsets { _, insets, _ ->
            binding.statusBar.run {
                layoutParams.height = insets.systemWindowInsetTop
                isVisible = layoutParams.height > 0
                requestLayout()
            }
        }

        if (adapter == null) {
            // Initialising sessionsViewBinder here to handle config change.
            sessionsViewBinder =
                FeedSessionsViewBinder(
                    model, savedInstanceState?.getParcelable(
                        BUNDLE_KEY_SESSIONS_LAYOUT_MANAGER_STATE
                    )
                )
        }

        binding.recyclerView.doOnApplyWindowInsets { v, insets, padding ->
            v.updatePaddingRelative(bottom = padding.bottom + insets.systemWindowInsetBottom)
        }

        binding.snackbar.doOnApplyWindowInsets { v, insets, padding ->
            v.updatePaddingRelative(bottom = padding.bottom + insets.systemWindowInsetBottom)
        }

        setUpSnackbar(model.snackBarMessage, binding.snackbar, snackbarMessageManager)

        model.errorMessage.observe(this, Observer { message ->
            val errorMessage = message?.getContentIfNotHandled()
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(this.context, errorMessage, Toast.LENGTH_SHORT).show()
                Timber.e(errorMessage)
            }
        })

        model.feed.observe(this, Observer {
            showFeedItems(binding.recyclerView, it)
        })

        model.navigateToSessionAction.observe(this, EventObserver { sessionId ->
            openSessionDetail(sessionId)
        })

        model.navigateToScheduleAction.observe(this, EventObserver { withPinnedEvents ->
            openSchedule(withPinnedEvents)
        })

        model.openSignInDialogAction.observe(this, EventObserver { openSignInDialog() })

        model.openLiveStreamAction.observe(this, EventObserver { streamUrl ->
            openLiveStreamUrl(streamUrl)
        })

        model.navigateToMapAction.observe(this, EventObserver { moment ->
            navigateToMapUsingMoment(moment)
        })
    }

    private fun openSessionDetail(id: SessionId) {
        findNavController().navigate(toSessionDetail(id))
    }

    private fun openSchedule(withPinnedSessions: Boolean) {
        if (withPinnedSessions) {
            findNavController().navigate(toSchedule(showPinnedEvents = true))
        } else {
            findNavController().navigate(toSchedule(showAllEvents = true))
        }
    }

    private fun showFeedItems(recyclerView: RecyclerView, list: List<Any>?) {

        if (adapter == null) {
            val announcementViewBinder =
                FeedAnnouncementViewBinder(timeZoneId = model.timeZoneId, lifecycleOwner = this)
            val sectionHeaderViewBinder = FeedSectionHeaderViewBinder()
            val feedHeaderViewBinder = FeedHeaderViewBinder(model)
            val announcementsPlaceholder = FeedAnnouncementsPlaceholderViewBinder()
            @Suppress("UNCHECKED_CAST")
            val viewBinders = ImmutableMap.builder<FeedItemClass, FeedItemBinder>()
                .put(
                    announcementViewBinder.modelClass,
                    announcementViewBinder as FeedItemBinder
                )
                .put(
                    sectionHeaderViewBinder.modelClass,
                    sectionHeaderViewBinder as FeedItemBinder
                )
                .put(
                    feedHeaderViewBinder.modelClass,
                    feedHeaderViewBinder as FeedItemBinder
                )
                .put(
                    sessionsViewBinder.modelClass,
                    sessionsViewBinder as FeedItemBinder
                )
                .put(
                    announcementsPlaceholder.modelClass,
                    announcementsPlaceholder as FeedItemBinder
                )
                .build()

            adapter = FeedAdapter(viewBinders)
        }
        if (recyclerView.adapter == null) {
            recyclerView.adapter = adapter
        }
        (recyclerView.adapter as FeedAdapter).submitList(list ?: emptyList())
    }

    private fun openSignInDialog() {
        SignInDialogFragment().show(
            requireActivity().supportFragmentManager, DIALOG_NEED_TO_SIGN_IN
        )
    }

    private fun navigateToMapUsingMoment(moment: Moment) {
        findNavController().navigate(
            toMap(featureId = moment.featureId, startTime = moment.startTime.toEpochMilli())
        )
    }

    private fun openLiveStreamUrl(url: String) {
        openWebsiteUrl(requireContext(), url)
    }
}
