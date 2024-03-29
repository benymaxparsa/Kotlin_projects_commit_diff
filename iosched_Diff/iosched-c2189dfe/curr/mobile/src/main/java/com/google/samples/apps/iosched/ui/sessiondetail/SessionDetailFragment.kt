/*
 * Copyright 2018 Google LLC
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

package com.google.samples.apps.iosched.ui.sessiondetail

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.app.ShareCompat
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.net.toUri
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.widget.Snackbar
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.databinding.FragmentSessionDetailBinding
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestParameters
import com.google.samples.apps.iosched.shared.util.viewModelProvider
import com.google.samples.apps.iosched.ui.SnackbarMessage
import com.google.samples.apps.iosched.ui.dialog.RemoveReservationDialogFragment
import com.google.samples.apps.iosched.ui.dialog.RemoveReservationDialogFragment.Companion.DIALOG_REMOVE_RESERVATION
import com.google.samples.apps.iosched.ui.dialog.SignInDialogFragment
import com.google.samples.apps.iosched.ui.dialog.SignInDialogFragment.Companion.DIALOG_NEED_TO_SIGN_IN
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class SessionDetailFragment : DaggerFragment() {

    private var shareString = ""

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var sessionDetailViewModel: SessionDetailViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)

        sessionDetailViewModel = viewModelProvider(viewModelFactory)
        sessionDetailViewModel.setSessionId(checkNotNull(arguments).getString(EXTRA_SESSION_ID))

        val binding: FragmentSessionDetailBinding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_session_detail, container, false
        )
        binding.run {
            viewModel = sessionDetailViewModel
            setLifecycleOwner(this@SessionDetailFragment)
            sessionDetailBottomAppBar.inflateMenu(R.menu.session_detail_menu)
            // todo setup menu & fab based on attendee
            sessionDetailBottomAppBar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.menu_item_share) {
                    ShareCompat.IntentBuilder.from(activity)
                        .setType("text/plain")
                        .setText(shareString)
                        .setChooserTitle(R.string.intent_chooser_session_detail)
                        .startChooser()
                }
                if (item.itemId == R.id.menu_item_star) {
                    viewModel?.onStarClicked()
                }
                true
            }
            up.setOnClickListener {
                requireActivity().finishAfterTransition()
            }
        }

        sessionDetailViewModel.session.observe(this, Observer {
            shareString = if (it == null) {
                ""
            } else {
                getString(R.string.share_text_session_detail, it.title, it.sessionUrl)
            }
        })

        sessionDetailViewModel.navigateToYouTubeAction.observe(this, Observer { navigationEvent ->
            navigationEvent?.getContentIfNotHandled()?.let { youtubeUrl ->
                openYoutubeUrl(youtubeUrl)
            }
        })

        // TODO style Snackbar so it doesn't overlap the bottom app bar (b/76112328)
        sessionDetailViewModel.snackBarMessage.observe(this, Observer {
            it?.getContentIfNotHandled()?.let { message: SnackbarMessage ->
                val coordinatorLayout =
                        requireActivity().findViewById<View>(R.id.coordinator_layout_session_detail)
                val duration = if (message.longDuration) {
                    Snackbar.LENGTH_LONG
                } else {
                    Snackbar.LENGTH_SHORT
                }
                Snackbar.make(coordinatorLayout, message.messageId, duration).apply {
                    message.actionId?.let { action -> setAction(action, { this.dismiss() }) }
                    setActionTextColor(ContextCompat.getColor(context, R.color.teal))
                    show()
                }
            }
        })

        sessionDetailViewModel.errorMessage.observe(this, Observer { message ->
            //TODO: Change once there's a way to show errors to the user
            message?.getContentIfNotHandled()?.let { errorMsg ->
                Toast.makeText(this.context, errorMsg, Toast.LENGTH_LONG).show()
            }
        })

        sessionDetailViewModel.navigateToSignInDialogAction.observe(this, Observer {
            it?.getContentIfNotHandled()?.let {
                openSignInDialog(requireActivity())
            }
        })
        sessionDetailViewModel.navigateToRemoveReservationDialogAction.observe(this, Observer {
            it?.getContentIfNotHandled()?.let {
                openRemoveReservationDialog(requireActivity(), it)
            }
        })
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Observing the changes from Fragment because data binding doesn't work with menu items.
        val menu = requireActivity().findViewById<BottomAppBar>(
                R.id.session_detail_bottom_app_bar).menu
        val starMenu = menu.findItem(R.id.menu_item_star)
        sessionDetailViewModel.observeRegisteredUser().observe(this, Observer {
            it?.let {
                if (it) {
                    starMenu.setVisible(true)
                } else {
                    starMenu.setVisible(false)
                }
            }
        })
        sessionDetailViewModel.userEvent.observe(this, Observer {
            it?.let {
                if (it.isStarred) {
                    starMenu.setIcon(R.drawable.ic_star)
                } else {
                    starMenu.setIcon(R.drawable.ic_star_border)
                }
            }
        })
    }

    private fun openYoutubeUrl(youtubeUrl: String) {
        startActivity(Intent(Intent.ACTION_VIEW, youtubeUrl.toUri()))
    }

    private fun openSignInDialog(activity: FragmentActivity) {
        val dialog = SignInDialogFragment()
        dialog.show(activity.supportFragmentManager, DIALOG_NEED_TO_SIGN_IN)
    }

    private fun openRemoveReservationDialog(activity: FragmentActivity,
                                            parameters: ReservationRequestParameters) {
        val dialog = RemoveReservationDialogFragment.newInstance(parameters)
        dialog.show(activity.supportFragmentManager, DIALOG_REMOVE_RESERVATION)
    }

    companion object {
        private const val EXTRA_SESSION_ID = "SESSION_ID"

        fun newInstance(sessionId: String): SessionDetailFragment {
            val bundle = Bundle().apply { putString(EXTRA_SESSION_ID, sessionId) }
            return SessionDetailFragment().apply { arguments = bundle }
        }
    }
}
