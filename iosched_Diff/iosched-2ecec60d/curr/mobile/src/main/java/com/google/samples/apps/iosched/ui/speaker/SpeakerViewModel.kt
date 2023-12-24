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

package com.google.samples.apps.iosched.ui.speaker

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsUseCase
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsUseCaseResult
import com.google.samples.apps.iosched.shared.domain.speakers.LoadSpeakerUseCase
import com.google.samples.apps.iosched.shared.domain.speakers.LoadSpeakerUseCaseResult
import com.google.samples.apps.iosched.shared.model.Speaker
import com.google.samples.apps.iosched.shared.model.SpeakerId
import com.google.samples.apps.iosched.shared.model.UserSession
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.shared.util.map
import com.google.samples.apps.iosched.ui.sessioncommon.EventActionsViewModelDelegate
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate
import javax.inject.Inject

/**
 * Loads a [Speaker] and their sessions, handles event actions.
 */
class SpeakerViewModel @Inject constructor(
    private val loadSpeakerUseCase: LoadSpeakerUseCase,
    private val loadSpeakerSessionsUseCase: LoadUserSessionsUseCase,
    signInViewModelDelegate: SignInViewModelDelegate,
    eventActionsViewModelDelegate: EventActionsViewModelDelegate
) : ViewModel(),
    SignInViewModelDelegate by signInViewModelDelegate,
    EventActionsViewModelDelegate by eventActionsViewModelDelegate {

    private val loadSpeakerUseCaseResult = MutableLiveData<Result<LoadSpeakerUseCaseResult>>()

    private val loadSpeakerUserSessions: LiveData<Result<LoadUserSessionsUseCaseResult>>

    private val _speaker = MediatorLiveData<Speaker>()
    val speaker: LiveData<Speaker>
        get() = _speaker

    private val _speakerUserSessions = MediatorLiveData<List<UserSession>>()
    val speakerUserSessions: LiveData<List<UserSession>>
        get() = _speakerUserSessions

    val hasProfileImage: LiveData<Boolean> = _speaker.map {
        !it?.imageUrl.isNullOrEmpty()
    }

    init {
        loadSpeakerUserSessions = loadSpeakerSessionsUseCase.observe()

        // If there's a new result with data, update speaker
        _speaker.addSource(loadSpeakerUseCaseResult) {
            (loadSpeakerUseCaseResult.value as? Result.Success)?.data?.let {
                _speaker.value = it.speaker
            }
        }

        // Also load their sessions
        loadSpeakerUserSessions.addSource(loadSpeakerUseCaseResult) {
            (loadSpeakerUseCaseResult.value as? Result.Success)?.data?.let {
                loadSpeakerSessionsUseCase.execute(getUserId() to it.sessionIds)
            }
        }

        // When their sessions load, update speakerUserSessions
        _speakerUserSessions.addSource(loadSpeakerUserSessions) {
            (loadSpeakerUserSessions.value as? Result.Success)?.data?.let {
                _speakerUserSessions.value = it.userSessions
            }
        }
    }

    /**
     * Provides the speaker ID which initiates all data loading.
     */
    fun setSpeakerId(id: SpeakerId) {
        loadSpeakerUseCase(id, loadSpeakerUseCaseResult)
    }

    /**
     * Clear subscriptions that might be leaked or that will not be used in the future.
     */
    override fun onCleared() {
        loadSpeakerSessionsUseCase.onCleared()
    }
}
