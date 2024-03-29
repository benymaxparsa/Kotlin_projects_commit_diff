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

package com.google.samples.apps.iosched.test.util.fakes

import android.arch.lifecycle.MutableLiveData
import android.net.Uri
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.ui.login.LoginEvent
import com.google.samples.apps.iosched.ui.login.LoginViewModelPlugin
import com.google.samples.apps.iosched.ui.schedule.Event
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

class FakeLoginViewModelPlugin : LoginViewModelPlugin {
    override val currentFirebaseUser = MutableLiveData<Result<AuthenticatedUserInfo>?>()
    override val currentUserImageUri = MutableLiveData<Uri?>()
    override val performLoginEvent = MutableLiveData<Event<LoginEvent>>()

    var injectIsLoggedIn = true
    var loginRequestsEmitted = 0
    var logoutRequestsEmitted = 0

    override fun isLoggedIn(): Boolean = injectIsLoggedIn

    override fun observeLoggedInUser() = TODO("Not implemented")

    override fun observeRegisteredUser() = TODO("Not implemented")

    override fun isRegistered(): Boolean = injectIsLoggedIn


    override fun emitLoginRequest() {
        loginRequestsEmitted++
    }

    override fun emitLogoutRequest() {
        logoutRequestsEmitted++
    }

    fun loadUser(id: String) {
        val mockUser = mock<AuthenticatedUserInfo> {
            on { getUid() }.doReturn(id)
            on { getPhotoUrl() }.doReturn(mock<Uri> {})
            on { isLoggedIn() }.doReturn(true)
        }
        currentFirebaseUser.postValue(Result.Success(mockUser))
    }
}
