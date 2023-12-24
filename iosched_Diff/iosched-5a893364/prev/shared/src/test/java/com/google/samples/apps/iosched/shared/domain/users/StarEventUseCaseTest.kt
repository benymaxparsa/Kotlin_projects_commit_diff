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

package com.google.samples.apps.iosched.shared.domain.users

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository
import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository
import com.google.samples.apps.iosched.shared.domain.repository.TestUserEventDataSource
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult
import com.google.samples.apps.iosched.shared.firestore.entity.LastReservationRequested
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.model.TestData
import com.google.samples.apps.iosched.shared.model.TestDataRepository
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.shared.util.SyncExecutorRule
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [StarEventUseCase]
 */
class StarEventUseCaseTest {

    // Executes tasks in the Architecture Components in the same thread
    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    // Executes tasks in a synchronous [TaskScheduler]
    @get:Rule
    var syncExecutorRule = SyncExecutorRule()

    @Test
    fun sessionIsStarredSuccessfully() {
        val testUserEventRepository = DefaultSessionAndUserEventRepository(
                TestUserEventDataSource(), DefaultSessionRepository(TestDataRepository))
        val useCase = StarEventUseCase(testUserEventRepository)

        val resultLiveData = useCase.observe()

        useCase.execute(StarEventParameter("userIdTest", TestData.session0, true))

        val result = LiveDataTestUtil.getValue(resultLiveData)
        Assert.assertEquals(result, Result.Success(StarUpdatedStatus.STARRED))
    }


    @Test
    fun sessionIsStarredUnsuccessfully() {

        val useCase = StarEventUseCase(FailingSessionAndUserEventRepository)

        val resultLiveData = useCase.observe()

        useCase.execute(StarEventParameter("userIdTest", TestData.session0, true))

        val result = LiveDataTestUtil.getValue(resultLiveData)
        assertTrue(result is Result.Error)
    }
}

val FailingSessionAndUserEventRepository = object : SessionAndUserEventRepository {

    val result = MutableLiveData<Result<StarUpdatedStatus>>()
    override fun updateIsStarred(userId: String, session: Session, isStarred: Boolean):
            LiveData<Result<StarUpdatedStatus>> {

        result.postValue(Result.Error(Exception("Test")))
        return result
    }

    override fun getObservableUserEvents(userId: String):
            LiveData<Result<LoadUserSessionsByDayUseCaseResult>> {
        throw NotImplementedError()
    }

    override fun changeReservation(userId: String, session: Session, action: ReservationRequestAction): LiveData<Result<LastReservationRequested>> {
        throw NotImplementedError()
    }
}