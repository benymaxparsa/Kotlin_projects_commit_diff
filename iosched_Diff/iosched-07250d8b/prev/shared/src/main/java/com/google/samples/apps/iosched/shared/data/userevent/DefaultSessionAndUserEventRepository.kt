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

package com.google.samples.apps.iosched.shared.data.userevent

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.support.annotation.WorkerThread
import com.google.samples.apps.iosched.shared.data.session.SessionRepository
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionUseCaseResult
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestAction
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestAction.RequestAction
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestAction.SwapAction
import com.google.samples.apps.iosched.shared.domain.users.StarUpdatedStatus
import com.google.samples.apps.iosched.shared.domain.users.SwapRequestAction
import com.google.samples.apps.iosched.shared.domain.users.SwapRequestParameters
import com.google.samples.apps.iosched.shared.firestore.entity.UserEvent
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.model.UserSession
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay
import com.google.samples.apps.iosched.shared.util.toEpochMilli
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of access to user events data associated with a user for the presentation layer.
 */
@Singleton
open class DefaultSessionAndUserEventRepository @Inject constructor(
        private val userEventDataSource: UserEventDataSource,
        private val sessionRepository: SessionRepository
) : SessionAndUserEventRepository {

    private val sessionsByDayResult = MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>>()
    private val sessionResult = MediatorLiveData<Result<LoadUserSessionUseCaseResult>>()

    // Keep a reference to the observable for a single event (from the details screen) so we can
    // stop observing when done.
    private var observableUserEvent: LiveData<UserEventResult>? = null

    override fun getObservableUserEvents(userId: String?):
            LiveData<Result<LoadUserSessionsByDayUseCaseResult>> {

        // If there is no logged-in user, return the map with null UserEvents
        if (userId == null) {
            Timber.d("EventRepository: No user logged in, returning sessions without user events.")
            val userSessionsPerDay = mapUserDataAndSessions(null, sessionRepository.getSessions())
            sessionsByDayResult.postValue(Result.Success(
                    LoadUserSessionsByDayUseCaseResult(
                            userSessionsPerDay = userSessionsPerDay,
                            userMessage = null)
            ))
            return sessionsByDayResult
        }
        // Observes the user events and merges them with session data.
        val observableUserEvents = userEventDataSource.getObservableUserEvents(userId)
        sessionsByDayResult.removeSource(observableUserEvents)
        sessionsByDayResult.addSource(observableUserEvents) { userEvents ->
            DefaultScheduler.execute {
                try {
                    Timber.d("EventRepository: Received " +
                            "${userEvents?.userEvents?.size ?: 0} user events changes")

                    // Get the sessions, synchronously
                    val allSessions = sessionRepository.getSessions()

                    // Merges sessions with user data and emits the result
                    val userEventsMessageSession = allSessions.firstOrNull() {
                        it.id == userEvents?.userEventsMessage?.sessionId
                    }
                    sessionsByDayResult.postValue(Result.Success(
                            LoadUserSessionsByDayUseCaseResult(
                                    userSessionsPerDay = mapUserDataAndSessions(
                                            userEvents, allSessions),
                                    userMessage = userEvents?.userEventsMessage,
                                    userMessageSession = userEventsMessageSession)
                    ))

                } catch (e: Exception) {
                    sessionsByDayResult.postValue(Result.Error(e))
                }
            }
        }
        return sessionsByDayResult
    }

    override fun getObservableUserEvent(
            userId: String?,
            eventId: String
    ): LiveData<Result<LoadUserSessionUseCaseResult>> {

        // If there is no logged-in user, return the session with a null UserEvent
        if (userId == null) {
            Timber.d("EventRepository: No user logged in, returning session without user event.")
            val session = sessionRepository.getSession(eventId)
            sessionResult.postValue(Result.Success(
                    LoadUserSessionUseCaseResult(
                            userSession = UserSession(session, createDefaultUserEvent(session)),
                            userMessage = null)
            ))
            return sessionResult
        }

        // Observes the user events and merges them with session data.
        val newObservableUserEvent = userEventDataSource.getObservableUserEvent(userId, eventId)
        sessionResult.removeSource(newObservableUserEvent) // Avoid multiple subscriptions
        sessionResult.value = null // Prevent old data from being emitted
        sessionResult.addSource(newObservableUserEvent) { userEventResult ->

            DefaultScheduler.execute {
                try {
                    Timber.d("EventRepository: Received user event changes")
                    // Get the session, synchronously
                    val event = sessionRepository.getSession(eventId)

                    // Merges session with user data and emits the result
                    val userSession = UserSession(event,
                            userEventResult?.userEvent ?: createDefaultUserEvent(event))
                    sessionResult.postValue(Result.Success(
                            LoadUserSessionUseCaseResult(
                                    userSession = userSession,
                                    userMessage = userEventResult?.userEventMessage
                            )
                    ))
                } catch (e: Exception) {
                    sessionResult.postValue(Result.Error(e))
                }
            }
        }
        this.observableUserEvent = newObservableUserEvent
        return sessionResult
    }

    override fun getUserEvents(userId: String?): List<UserEvent> {
        return userEventDataSource.getUserEvents(userId ?: "")
    }

    override fun starEvent(userId: String, userEvent: UserEvent):
            LiveData<Result<StarUpdatedStatus>> {
        return userEventDataSource.starEvent(userId, userEvent)
    }

    override fun changeReservation(
            userId: String,
            sessionId: String,
            action: ReservationRequestAction
    ): LiveData<Result<ReservationRequestAction>> {

        val userEvents = getUserEvents(userId)
        val session = sessionRepository.getSession(sessionId)
        val overlappingId = findOverlappingReservationId(session, action, userEvents)
        if (overlappingId != null) {
            // If there is already an overlapping reservation, return the result as
            // SwapAction is needed.
            val result = MutableLiveData<Result<ReservationRequestAction>>()
            val overlappingSession = sessionRepository.getSession(overlappingId)
            Timber.d("""User is trying to reserve a session that overlaps with the
                |session id: $overlappingId, title: ${overlappingSession.title}""".trimMargin())
            result.postValue(Result.Success(SwapAction(
                    SwapRequestParameters(userId,
                            fromId = overlappingId,
                            fromTitle = overlappingSession.title,
                            toId = sessionId,
                            toTitle = session.title))))
            return result
        }
        return userEventDataSource.requestReservation(userId, session, action)
    }

    override fun swapReservation(userId: String,
                                 fromId: String,
                                 toId: String
    ): LiveData<Result<SwapRequestAction>> {
        val toSession = sessionRepository.getSession(toId)
        val fromSession = sessionRepository.getSession(fromId)
        return userEventDataSource.swapReservation(userId, fromSession, toSession)
    }

    private fun findOverlappingReservationId(session: Session,
                                             action: ReservationRequestAction,
                                             userEvents: List<UserEvent>
    ): String? {
        if (action !is RequestAction) return null
        val overlappingUserEvent = userEvents.find {
            it.isOverlapping(session) && (it.isReserved() || it.isWaitlisted())
        }
        return overlappingUserEvent?.id
    }

    private fun createDefaultUserEvent(session: Session): UserEvent {
        return UserEvent(
                id = session.id,
                startTime = session.startTime.toEpochMilli(),
                endTime = session.endTime.toEpochMilli())
    }

    /**
     * Merges user data with sessions.
     */
    @WorkerThread
    private fun mapUserDataAndSessions(
            userData: UserEventsResult?,
            allSessions: List<Session>
    ): Map<ConferenceDay, List<UserSession>> {

        // If there is no logged-in user, return the map with null UserEvents
        if (userData == null) {
            return ConferenceDay.values().map { day ->
                day to allSessions
                        .filter { day.contains(it) }
                        .map { session -> UserSession(session, createDefaultUserEvent(session)) }

            }.toMap()
        }

        val (userEvents, _) = userData

        val eventIdToUserEvent: Map<String, UserEvent?> = userEvents.map { it.id to it }.toMap()
        val allUserSessions = allSessions.map { UserSession(
                it,
                eventIdToUserEvent[it.id] ?: createDefaultUserEvent(it)
        ) }

        return ConferenceDay.values().map { day ->
            day to allUserSessions
                    .filter { day.contains(it.session) }
                    .map { userSession ->
                        UserSession(userSession.session, userSession.userEvent)
                    }
                    .sortedBy { it.session.startTime }
        }.toMap()
    }

    override fun clearSingleEventSubscriptions() {
        // The details screen is closing so reset subscriptions and old values
        sessionResult.value = null
        // The UserEvent data source can stop observing user data
        userEventDataSource.clearSingleEventSubscriptions()
        // Also free its observable in the repository.
        observableUserEvent?.let { sessionResult.removeSource(it) }
    }
}

interface SessionAndUserEventRepository {

    fun getObservableUserEvents(userId: String?):
            LiveData<Result<LoadUserSessionsByDayUseCaseResult>>

    fun getObservableUserEvent(userId: String?, eventId: String):
            LiveData<Result<LoadUserSessionUseCaseResult>>

    fun getUserEvents(userId: String?): List<UserEvent>

    fun changeReservation(
            userId: String, sessionId: String, action: ReservationRequestAction
    ): LiveData<Result<ReservationRequestAction>>

    fun swapReservation(userId: String, fromId: String, toId: String):
            LiveData<Result<SwapRequestAction>>

    fun starEvent(userId: String, userEvent: UserEvent): LiveData<Result<StarUpdatedStatus>>

    fun clearSingleEventSubscriptions()
}

