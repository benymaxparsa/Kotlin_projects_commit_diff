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

import android.databinding.BindingAdapter
import android.support.v7.widget.RecyclerView.RecycledViewPool
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.databinding.ItemSessionBinding
import com.google.samples.apps.iosched.databinding.ItemSpeakerBinding
import com.google.samples.apps.iosched.shared.firestore.entity.UserEvent
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.model.SessionType.AFTER_HOURS
import com.google.samples.apps.iosched.shared.model.SessionType.CODELAB
import com.google.samples.apps.iosched.shared.model.SessionType.OFFICE_HOURS
import com.google.samples.apps.iosched.shared.model.SessionType.SANDBOX
import com.google.samples.apps.iosched.shared.model.Speaker
import com.google.samples.apps.iosched.shared.model.UserSession
import com.google.samples.apps.iosched.shared.util.SpeakerUtils
import com.google.samples.apps.iosched.shared.util.TimeUtils
import com.google.samples.apps.iosched.ui.reservation.ReservationViewState
import com.google.samples.apps.iosched.ui.reservation.ReservationViewState.RESERVABLE
import com.google.samples.apps.iosched.ui.reservation.StarReserveFab
import com.google.samples.apps.iosched.ui.sessioncommon.EventActions
import com.google.samples.apps.iosched.util.drawable.HeaderGridDrawable
import org.threeten.bp.Duration
import org.threeten.bp.ZonedDateTime

@Suppress("unused")
@BindingAdapter("headerImage")
fun headerImage(imageView: ImageView, photoUrl: String?) {
    if (!photoUrl.isNullOrEmpty()) {
        Glide.with(imageView)
            .load(photoUrl)
            .apply(RequestOptions().placeholder(HeaderGridDrawable(imageView.context)))
            .into(imageView)
    } else {
        imageView.setImageDrawable(HeaderGridDrawable(imageView.context))
    }
}

@Suppress("unused")
@BindingAdapter("eventHeaderAnim")
fun eventHeaderAnim(lottieView: LottieAnimationView, session: Session?) {
    val anim = when (session?.type) {
        AFTER_HOURS -> "anim/event_details_after_hours.json"
        CODELAB -> "anim/event_details_codelabs.json"
        OFFICE_HOURS -> "anim/event_details_office_hours.json"
        SANDBOX -> "anim/event_details_sandbox.json"
        /* default to session anim */
        else -> "anim/event_details_session.json"
    }
    lottieView.setAnimation(anim)
}

@Suppress("unused")
@BindingAdapter(value = ["sessionDetailStartTime", "sessionDetailEndTime"], requireAll = true)
fun timeString(
    view: TextView,
    sessionDetailStartTime: ZonedDateTime?,
    sessionDetailEndTime: ZonedDateTime?
) {
    if (sessionDetailStartTime == null || sessionDetailEndTime == null) {
        view.text = ""
    } else {
        view.text = TimeUtils.timeString(
            TimeUtils.zonedTime(sessionDetailStartTime),
            TimeUtils.zonedTime(sessionDetailEndTime)
        )
    }
}

@BindingAdapter("sessionStartCountdown")
fun sessionStartCountdown(view: TextView, timeUntilStart: Duration?) {
    if (timeUntilStart == null) {
        view.visibility = GONE
    } else {
        view.visibility = VISIBLE
        val minutes = timeUntilStart.toMinutes()
        view.text = view.context.resources.getQuantityString(
            R.plurals.session_starting_in, minutes.toInt(), minutes.toString()
        )
    }
}

@BindingAdapter(
    "userEvent",
    "isSignedIn",
    "isRegistered",
    "isReservable",
    "isReservationDisabled",
    "eventListener",
    requireAll = true
)
fun assignFab(
    fab: StarReserveFab,
    userEvent: UserEvent?,
    isSignedIn: Boolean,
    isRegistered: Boolean,
    isReservable: Boolean,
    isReservationDisabled: Boolean,
    eventListener: SessionDetailEventListener
) {
    when {
        !isSignedIn -> {
            if (isReservable) {
                fab.reservationStatus = RESERVABLE
            } else {
                fab.isChecked = false
            }
            fab.setOnClickListener { eventListener.onLoginClicked() }
        }
        isRegistered && isReservable -> {
            fab.reservationStatus = ReservationViewState.fromUserEvent(userEvent,
                    isReservationDisabled)
            fab.setOnClickListener { eventListener.onReservationClicked() }
        }
        else -> {
            fab.isChecked = userEvent?.isStarred == true
            fab.setOnClickListener { eventListener.onStarClicked() }
        }
    }
}

@BindingAdapter(value = ["sessionSpeakers", "eventListener"], requireAll = true)
fun createSessionSpeakerViews(
    layout: LinearLayout,
    speakers: Set<Speaker>?,
    eventListener: SessionDetailEventListener
) {
    if (speakers != null) {
        // remove all views other than the header
        for (i in layout.childCount - 1 downTo 1) {
            layout.removeViewAt(i)
        }
        SpeakerUtils.alphabeticallyOrderedSpeakerList(speakers).forEach {
            layout.addView(createSpeakerView(layout, it, eventListener))
        }
    }
}

private fun createSpeakerView(
    container: ViewGroup,
    presenter: Speaker,
    listener: SessionDetailEventListener
): View {
    val binding = ItemSpeakerBinding.inflate(
        LayoutInflater.from(container.context),
        container,
        false
    ).apply {
        eventListener = listener
        speaker = presenter
        root.setTag(R.id.tag_speaker_id, presenter.id) // Used to identify which view was clicked
    }
    return binding.root
}

@BindingAdapter(value = ["relatedEvents", "eventListener", "tagViewPool"], requireAll = true)
fun createRelatedSessionViews(
    layout: LinearLayout,
    relatedEvents: List<UserSession>?,
    eventListener: EventActions,
    tagViewPool: RecycledViewPool?
) {
    if (relatedEvents != null) {
        // remove all views other than the header
        for (i in layout.childCount - 1 downTo 1) {
            layout.removeViewAt(i)
        }
        relatedEvents.forEach {
            layout.addView(createEventView(layout, it, eventListener, tagViewPool))
        }
    }
}

private fun createEventView(
    container: ViewGroup,
    event: UserSession,
    listener: EventActions,
    tagViewPool: RecycledViewPool?
): View {
    val binding = ItemSessionBinding.inflate(
        LayoutInflater.from(container.context),
        container,
        false
    ).apply {
        session = event.session
        userEvent = event.userEvent
        eventListener = listener
        tags.recycledViewPool = tagViewPool
    }
    return binding.root
}
