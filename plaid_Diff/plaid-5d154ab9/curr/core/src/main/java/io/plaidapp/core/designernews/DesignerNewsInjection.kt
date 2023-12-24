/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("Injection")

package io.plaidapp.core.designernews

import android.content.Context
import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental.CoroutineCallAdapterFactory
import io.plaidapp.core.BuildConfig
import io.plaidapp.core.data.CoroutinesContextProvider
import io.plaidapp.core.data.api.DenvelopingConverter
import io.plaidapp.core.designernews.data.api.ClientAuthInterceptor
import io.plaidapp.core.designernews.data.login.DesignerNewsAuthTokenLocalDataSource
import io.plaidapp.core.designernews.data.stories.DesignerNewsRepository
import io.plaidapp.core.designernews.data.api.DesignerNewsService
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource
import io.plaidapp.core.designernews.data.login.LoginRemoteDataSource
import io.plaidapp.core.designernews.data.login.LoginRepository
import io.plaidapp.core.designernews.data.comments.CommentsRepository
import io.plaidapp.core.designernews.data.votes.DesignerNewsVotesRepository
import io.plaidapp.core.designernews.data.votes.VotesRemoteDataSource
import io.plaidapp.core.designernews.data.comments.DesignerNewsCommentsRemoteDataSource
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource
import io.plaidapp.core.designernews.data.users.UserRepository
import io.plaidapp.core.designernews.domain.CommentsUseCase
import io.plaidapp.core.designernews.domain.CommentsWithRepliesUseCase
import io.plaidapp.core.loggingInterceptor
import io.plaidapp.core.provideCoroutinesContextProvider
import io.plaidapp.core.provideSharedPreferences
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * File providing different dependencies.
 *
 * Once we have a dependency injection framework or a service locator, this should be removed.
 */

fun provideDesignerNewsLoginLocalDataSource(context: Context): LoginLocalDataSource {
    val preferences = provideSharedPreferences(
            context,
            LoginLocalDataSource.DESIGNER_NEWS_PREF)
    return LoginLocalDataSource(preferences)
}

fun provideDesignerNewsLoginRepository(context: Context): LoginRepository {
    return LoginRepository.getInstance(
            provideDesignerNewsLoginLocalDataSource(context),
            provideDesignerNewsLoginRemoteDataSource(context))
}

fun provideDesignerNewsLoginRemoteDataSource(context: Context): LoginRemoteDataSource {
    val tokenHolder = provideDesignerNewsAuthTokenLocalDataSource(context)
    return LoginRemoteDataSource(tokenHolder, provideDesignerNewsService(tokenHolder))
}

private fun provideDesignerNewsAuthTokenLocalDataSource(
    context: Context
): DesignerNewsAuthTokenLocalDataSource {
    return DesignerNewsAuthTokenLocalDataSource.getInstance(
            provideSharedPreferences(
                    context,
                    DesignerNewsAuthTokenLocalDataSource.DESIGNER_NEWS_AUTH_PREF))
}

fun provideDesignerNewsService(context: Context): DesignerNewsService {
    val tokenHolder = provideDesignerNewsAuthTokenLocalDataSource(context)
    return provideDesignerNewsService(tokenHolder)
}

private fun provideDesignerNewsService(
    authTokenDataSource: DesignerNewsAuthTokenLocalDataSource
): DesignerNewsService {
    val client = OkHttpClient.Builder()
            .addInterceptor(
                    ClientAuthInterceptor(authTokenDataSource, BuildConfig.DESIGNER_NEWS_CLIENT_ID))
            .addInterceptor(loggingInterceptor)
            .build()
    val gson = Gson()
    return Retrofit.Builder()
            .baseUrl(DesignerNewsService.ENDPOINT)
            .client(client)
            .addConverterFactory(DenvelopingConverter(gson))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
            .create(DesignerNewsService::class.java)
}

fun provideDesignerNewsRepository(context: Context): DesignerNewsRepository {
    return provideDesignerNewsRepository(DesignerNewsPrefs.get(context).api)
}

private fun provideDesignerNewsRepository(service: DesignerNewsService): DesignerNewsRepository {
    return DesignerNewsRepository.getInstance(service)
}

fun provideCommentsUseCase(context: Context): CommentsUseCase {
    val service = provideDesignerNewsService(context)
    val commentsRepository = provideCommentsRepository(
            provideDesignerNewsCommentsRemoteDataSource(service))
    val userRepository = provideUserRepository(provideUserRemoteDataSource(service))
    return provideCommentsUseCase(
            provideCommentsWithRepliesUseCase(commentsRepository),
            userRepository,
            provideCoroutinesContextProvider())
}

fun provideCommentsRepository(dataSource: DesignerNewsCommentsRemoteDataSource) =
        CommentsRepository.getInstance(dataSource)

fun provideCommentsWithRepliesUseCase(commentsRepository: CommentsRepository) =
        CommentsWithRepliesUseCase(commentsRepository)

fun provideCommentsUseCase(
    commentsWithCommentsWithRepliesUseCase: CommentsWithRepliesUseCase,
    userRepository: UserRepository,
    contextProvider: CoroutinesContextProvider
) = CommentsUseCase(commentsWithCommentsWithRepliesUseCase, userRepository, contextProvider)

private fun provideUserRemoteDataSource(service: DesignerNewsService) =
        UserRemoteDataSource(service)

private fun provideUserRepository(dataSource: UserRemoteDataSource) =
        UserRepository.getInstance(dataSource)

private fun provideDesignerNewsCommentsRemoteDataSource(service: DesignerNewsService) =
        DesignerNewsCommentsRemoteDataSource.getInstance(service)

fun provideDesignerNewsVotesRepository(context: Context): DesignerNewsVotesRepository {
    return provideDesignerNewsVotesRepository(
            provideVotesRemoteDataSource(provideDesignerNewsService(context)),
            provideCoroutinesContextProvider()
    )
}

private fun provideVotesRemoteDataSource(service: DesignerNewsService) = VotesRemoteDataSource(service)

private fun provideDesignerNewsVotesRepository(
    remoteDataSource: VotesRemoteDataSource,
    contextProvider: CoroutinesContextProvider
): DesignerNewsVotesRepository {
    return DesignerNewsVotesRepository.getInstance(remoteDataSource, contextProvider)
}
