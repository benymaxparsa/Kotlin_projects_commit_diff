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

package com.google.samples.apps.iosched.shared.di

import com.google.firebase.auth.FirebaseAuth
import com.google.samples.apps.iosched.shared.data.BootstrapConferenceDataSource
import com.google.samples.apps.iosched.shared.data.ConferenceDataRepository
import com.google.samples.apps.iosched.shared.data.ConferenceDataSource
import com.google.samples.apps.iosched.shared.data.OfflineConferenceDataSource
import com.google.samples.apps.iosched.shared.data.login.DefaultFirebaseUserDataSource
import com.google.samples.apps.iosched.shared.data.login.FirebaseUserDataSource
import com.google.samples.apps.iosched.shared.data.login.LoginDataSource
import com.google.samples.apps.iosched.shared.data.login.LoginRemoteDataSource
import com.google.samples.apps.iosched.shared.data.map.FakeMapMetadataDataSource
import com.google.samples.apps.iosched.shared.data.map.MapMetadataDataSource
import com.google.samples.apps.iosched.shared.data.session.SessionRepository
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository
import com.google.samples.apps.iosched.shared.data.userevent.FakeUserEventDataSource
import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository
import com.google.samples.apps.iosched.shared.data.userevent.UserEventDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

/**
 * Module where classes created in the shared module are created.
 */
@Module
class SharedModule {

// Define the data source implementations that should be used. All data sources are singletons.

    @Singleton
    @Provides
    @Named("remoteConfDatasource")
    fun provideConferenceDataSource(): ConferenceDataSource {
        return OfflineConferenceDataSource()
    }

    @Singleton
    @Provides
    @Named("bootstrapConfDataSource")
    fun provideBootstrapRemoteSessionDataSource(): ConferenceDataSource {
        return BootstrapConferenceDataSource
    }

    @Singleton
    @Provides
    fun provideConferenceDataRepository(
        @Named("remoteConfDatasource") remoteDataSource: ConferenceDataSource,
        @Named("bootstrapConfDataSource") boostrapDataSource: ConferenceDataSource
    ): ConferenceDataRepository {
        return ConferenceDataRepository(remoteDataSource, boostrapDataSource)
    }

    @Singleton
    @Provides
    fun provideMapMetadataDataSource(): MapMetadataDataSource {
        return FakeMapMetadataDataSource
    }

    @Singleton
    @Provides
    fun provideUserEventDataSource(): UserEventDataSource {
        return FakeUserEventDataSource
    }

    @Singleton
    @Provides
    fun provideSessionAndUserEventRepository(
            userEventDataSource: UserEventDataSource,
            sessionRepository: SessionRepository
    ): SessionAndUserEventRepository {
        return DefaultSessionAndUserEventRepository(userEventDataSource, sessionRepository)
    }

    @Singleton
    @Provides
    fun provideLoginDataSource(): LoginDataSource {
        return LoginRemoteDataSource()
    }

    @Singleton
    @Provides
    fun provideFirebaseAuth() = FirebaseAuth.getInstance()

    @Singleton
    @Provides
    fun provideFirebaseUserDataSource(firebase: FirebaseAuth): FirebaseUserDataSource {
        return DefaultFirebaseUserDataSource(firebase)
    }
}
