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

package com.google.samples.apps.iosched.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.model.SessionId
import com.google.samples.apps.iosched.model.userdata.UserSession
import com.google.samples.apps.iosched.shared.analytics.AnalyticsActions
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper
import com.google.samples.apps.iosched.shared.domain.RefreshConferenceDataUseCase
import com.google.samples.apps.iosched.shared.domain.prefs.LoadSelectedFiltersUseCase
import com.google.samples.apps.iosched.shared.domain.prefs.SaveSelectedFiltersUseCase
import com.google.samples.apps.iosched.shared.domain.prefs.ScheduleUiHintsShownUseCase
import com.google.samples.apps.iosched.shared.domain.sessions.LoadFilteredUserSessionsParameters
import com.google.samples.apps.iosched.shared.domain.sessions.LoadFilteredUserSessionsResult
import com.google.samples.apps.iosched.shared.domain.sessions.LoadFilteredUserSessionsUseCase
import com.google.samples.apps.iosched.shared.domain.sessions.ObserveConferenceDataUseCase
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase
import com.google.samples.apps.iosched.shared.domain.users.StarEventParameter
import com.google.samples.apps.iosched.shared.domain.users.StarEventUseCase
import com.google.samples.apps.iosched.shared.domain.users.StarUpdatedStatus
import com.google.samples.apps.iosched.shared.fcm.TopicSubscriber
import com.google.samples.apps.iosched.shared.result.Event
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.shared.result.Result.Success
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher
import com.google.samples.apps.iosched.shared.util.TimeUtils
import com.google.samples.apps.iosched.shared.util.map
import com.google.samples.apps.iosched.ui.SnackbarMessage
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.MyEventsFilter
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.TagFilter
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase
import com.google.samples.apps.iosched.ui.sessioncommon.EventActions
import com.google.samples.apps.iosched.ui.sessioncommon.stringRes
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate
import org.threeten.bp.ZoneId
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Loads data and exposes it to the view.
 * By annotating the constructor with [@Inject], Dagger will use that constructor when needing to
 * create the object, so defining a [@Provides] method for this class won't be needed.
 */
class ScheduleViewModel @Inject constructor(
    private val loadFilteredUserSessionsUseCase: LoadFilteredUserSessionsUseCase,
    loadEventFiltersUseCase: LoadEventFiltersUseCase,
    signInViewModelDelegate: SignInViewModelDelegate,
    private val starEventUseCase: StarEventUseCase,
    scheduleUiHintsShownUseCase: ScheduleUiHintsShownUseCase,
    topicSubscriber: TopicSubscriber,
    private val snackbarMessageManager: SnackbarMessageManager,
    private val getTimeZoneUseCase: GetTimeZoneUseCase,
    private val refreshConferenceDataUseCase: RefreshConferenceDataUseCase,
    observeConferenceDataUseCase: ObserveConferenceDataUseCase,
    loadSelectedFiltersUseCase: LoadSelectedFiltersUseCase,
    private val saveSelectedFiltersUseCase: SaveSelectedFiltersUseCase,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel(),
    ScheduleEventListener,
    SignInViewModelDelegate by signInViewModelDelegate {

    val isLoading: LiveData<Boolean>

    val swipeRefreshing: LiveData<Boolean>

    // The current UserSessionMatcher, used to filter the events that are shown
    private val userSessionMatcher = UserSessionMatcher()
    private val loadSelectedFiltersResult = MutableLiveData<Result<Unit>>()

    private val preferConferenceTimeZoneResult = MutableLiveData<Result<Boolean>>()

    val timeZoneId: LiveData<ZoneId>

    private val _sessionTimeData = MediatorLiveData<SessionTimeData>()
    val sessionTimeData: LiveData<SessionTimeData>
        get() = _sessionTimeData

    // Cached list of TagFilters returned by the use case. Only Result.Success modifies it.
    private var cachedEventFilters = emptyList<EventFilter>()

    val eventFilters: LiveData<List<EventFilter>>
    private val _selectedFilters = MutableLiveData<List<EventFilter>>()
    val selectedFilters: LiveData<List<EventFilter>>
        get() = _selectedFilters
    private val _hasAnyFilters = MutableLiveData<Boolean>().apply { value = false }
    val hasAnyFilters: LiveData<Boolean>
        get() = _hasAnyFilters

    private val loadSessionsResult: MediatorLiveData<Result<LoadFilteredUserSessionsResult>>
    private val loadEventFiltersResult = MediatorLiveData<Result<List<EventFilter>>>()
    private val swipeRefreshResult = MutableLiveData<Result<Boolean>>()

    val eventCount: LiveData<Int>

    /** LiveData for Actions and Events **/

    private val _errorMessage = MediatorLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>>
        get() = _errorMessage

    private val _navigateToSessionAction = MutableLiveData<Event<String>>()
    val navigateToSessionAction: LiveData<Event<String>>
        get() = _navigateToSessionAction

    private val _snackBarMessage = MediatorLiveData<Event<SnackbarMessage>>()
    val snackBarMessage: LiveData<Event<SnackbarMessage>>
        get() = _snackBarMessage

    val showReservations: LiveData<Boolean>

    private val _navigateToSignInDialogAction = MutableLiveData<Event<Unit>>()
    val navigateToSignInDialogAction: LiveData<Event<Unit>>
        get() = _navigateToSignInDialogAction

    private val _navigateToSignOutDialogAction = MutableLiveData<Event<Unit>>()
    val navigateToSignOutDialogAction: LiveData<Event<Unit>>
        get() = _navigateToSignOutDialogAction

    private val scheduleUiHintsShownResult = MutableLiveData<Result<Boolean>>()
    /** Indicates if the UI hints for the schedule have been shown */
    val scheduleUiHintsShown: LiveData<Event<Boolean>>

    // Flags used to indicate if the "scroll to now" feature has been used already.
    var userHasInteracted = false

    // The currently happening event
    val currentEvent: LiveData<Int>

    init {
        // Load sessions and tags and store the result in `LiveData`s
        loadSessionsResult = loadFilteredUserSessionsUseCase.observe()

        val conferenceDataAvailable = observeConferenceDataUseCase.observe()

        // Load EventFilters when persisted filters are loaded and when there's new conference data
        loadEventFiltersResult.addSource(loadSelectedFiltersResult) {
            loadEventFiltersUseCase(userSessionMatcher, loadEventFiltersResult)
        }
        loadEventFiltersResult.addSource(conferenceDataAvailable) {
            loadEventFiltersUseCase(userSessionMatcher, loadEventFiltersResult)
        }

        // Load persisted filters to the matcher
        loadSelectedFiltersUseCase(userSessionMatcher, loadSelectedFiltersResult)

        // Load sessions when persisted filters are loaded and when there's new conference data
        loadSessionsResult.addSource(conferenceDataAvailable) {
            Timber.d("Detected new data in conference data repository")
            refreshUserSessions()
        }
        loadSessionsResult.addSource(loadEventFiltersResult) {
            Timber.d("Loaded filters from persistent storage")
            refreshUserSessions()
        }

        eventCount = loadSessionsResult.map {
            (it as? Result.Success)?.data?.userSessionCount ?: 0
        }

        isLoading = loadSessionsResult.map { it == Result.Loading }

        _errorMessage.addSource(loadSessionsResult) { result ->
            if (result is Result.Error) {
                _errorMessage.value = Event(content = result.exception.message ?: "Error")
            }
        }
        _errorMessage.addSource(loadEventFiltersResult) { result ->
            if (result is Result.Error) {
                _errorMessage.value = Event(content = result.exception.message ?: "Error")
            }
        }

        eventFilters = loadEventFiltersResult.map {
            if (it is Success) {
                cachedEventFilters = it.data
                updateFilterStateObservables()
            }
            // TODO handle Error result
            cachedEventFilters
        }

        // Show an error message if a star request fails
        _snackBarMessage.addSource(starEventUseCase.observe()) { it: Result<StarUpdatedStatus>? ->
            // Show a snackbar message on error.
            if (it is Result.Error) {
                _snackBarMessage.postValue(
                    Event(
                        SnackbarMessage(
                            messageId = R.string.event_star_error,
                            longDuration = true
                        )
                    )
                )
            }
        }

        // Show a message with the result of a reservation
        _snackBarMessage.addSource(loadFilteredUserSessionsUseCase.observe()) {
            if (it is Result.Success) {
                it.data.userMessage?.type?.stringRes()?.let { messageId ->
                    // There is a message to display:

                    snackbarMessageManager.addMessage(
                        SnackbarMessage(
                            messageId = messageId,
                            longDuration = true,
                            session = it.data.userMessageSession,
                            requestChangeId = it.data.userMessage?.changeRequestId
                        )
                    )
                }
            }
        }

        // Refresh the list of user sessions if the user is updated.
        loadSessionsResult.addSource(currentUserInfo) {
            Timber.d("Loading user session with user ${it?.getUid()}")
            refreshUserSessions()
        }

        // Show reservation button if not logged in or (logged in && registered)
        showReservations = currentUserInfo.map {
            isRegistered() || !isSignedIn()
        }

        scheduleUiHintsShownUseCase(Unit, scheduleUiHintsShownResult)
        scheduleUiHintsShown = scheduleUiHintsShownResult.map {
            Event((it as? Result.Success)?.data == true)
        }

        val showInConferenceTimeZone = preferConferenceTimeZoneResult.map {
            (it as? Result.Success<Boolean>)?.data ?: true
        }

        timeZoneId = showInConferenceTimeZone.map { inConferenceTimeZone ->
            if (inConferenceTimeZone) {
                TimeUtils.CONFERENCE_TIMEZONE
            } else {
                ZoneId.systemDefault()
            }
        }

        _sessionTimeData.addSource(timeZoneId) {
            _sessionTimeData.value = _sessionTimeData.value?.apply {
                timeZoneId = it
            } ?: SessionTimeData(timeZoneId = it)
        }
        _sessionTimeData.addSource(loadSessionsResult) {
            val userSessions = (it as? Result.Success)?.data?.userSessions ?: return@addSource
            _sessionTimeData.value = _sessionTimeData.value?.apply {
                list = userSessions
            } ?: SessionTimeData(list = userSessions)
        }

        swipeRefreshing = swipeRefreshResult.map {
            // Whenever refresh finishes, stop the indicator, whatever the result
            false
        }

        // Subscribe user to schedule updates
        topicSubscriber.subscribeToScheduleUpdates()

        // Observe updates in conference data
        observeConferenceDataUseCase.execute(Any())

        currentEvent = loadSessionsResult.map { result ->
            (result as? Success)?.data?.firstUnfinishedSessionIndex ?: -1
        }
    }

    override fun openEventDetail(id: SessionId) {
        _navigateToSessionAction.value = Event(id)
    }

    override fun toggleFilter(filter: EventFilter, enabled: Boolean) {
        val changed = when (filter) {
            is MyEventsFilter -> userSessionMatcher.setShowPinnedEventsOnly(enabled)
            is TagFilter -> {
                if (enabled) {
                    userSessionMatcher.add(filter.tag)
                } else {
                    userSessionMatcher.remove(filter.tag)
                }
            }
        }
        if (changed) {
            // Actually toggle the filter
            filter.isChecked.set(enabled)
            // Persist the filters
            saveSelectedFiltersUseCase(userSessionMatcher)
            // Update observables
            updateFilterStateObservables()
            refreshUserSessions()

            // Analytics
            val filterName = if (filter is MyEventsFilter) {
                "Starred & Reserved"
            } else {
                filter.getText()
            }
            val action = if (enabled) AnalyticsActions.ENABLE else AnalyticsActions.DISABLE
            analyticsHelper.logUiEvent("Filter changed: $filterName", action)
        }
    }

    override fun clearFilters() {
        if (userSessionMatcher.clearAll()) {
            eventFilters.value?.forEach { it.isChecked.set(false) }
            saveSelectedFiltersUseCase(userSessionMatcher)
            updateFilterStateObservables()
            refreshUserSessions()

            analyticsHelper.logUiEvent("Clear filters", AnalyticsActions.CLICK)
        }
    }

    // Update all observables related to the filter state. Called from methods that modify
    // selected filters in the UserSessionMatcher.
    private fun updateFilterStateObservables() {
        val hasAnyFilters = userSessionMatcher.hasAnyFilters()
        _hasAnyFilters.value = hasAnyFilters
        _selectedFilters.value = cachedEventFilters.filter { it.isChecked.get() }
    }

    fun onSwipeRefresh() {
        refreshConferenceDataUseCase(Any(), swipeRefreshResult)
    }

    fun onSignInRequired() {
        _navigateToSignInDialogAction.value = Event(Unit)
    }

    private fun refreshUserSessions() {
        Timber.d("ViewModel refreshing user sessions")
        loadFilteredUserSessionsUseCase.execute(
            LoadFilteredUserSessionsParameters(userSessionMatcher, getUserId())
        )
    }

    override fun onStarClicked(userSession: UserSession) {
        if (!isSignedIn()) {
            Timber.d("Showing Sign-in dialog after star click")
            _navigateToSignInDialogAction.value = Event(Unit)
            return
        }
        val newIsStarredState = !userSession.userEvent.isStarred

        // Update the snackbar message optimistically.
        val stringResId = if (newIsStarredState) {
            R.string.event_starred
        } else {
            R.string.event_unstarred
        }

        if (newIsStarredState) {
            analyticsHelper.logUiEvent(userSession.session.title, AnalyticsActions.STARRED)
        }

        snackbarMessageManager.addMessage(
            SnackbarMessage(
                messageId = stringResId,
                actionId = R.string.dont_show,
                requestChangeId = UUID.randomUUID().toString()
            )
        )

        getUserId()?.let {
            starEventUseCase.execute(
                StarEventParameter(
                    it,
                    userSession.userEvent.copy(isStarred = newIsStarredState)
                )
            )
        }
    }

    fun initializeTimeZone() {
        getTimeZoneUseCase(Unit, preferConferenceTimeZoneResult)
    }
}

data class SessionTimeData(var list: List<UserSession>? = null, var timeZoneId: ZoneId? = null)

interface ScheduleEventListener : EventActions {
    /** Called from the UI to enable or disable a particular filter. */
    fun toggleFilter(filter: EventFilter, enabled: Boolean)

    /** Called from the UI to remove all filters. */
    fun clearFilters()
}