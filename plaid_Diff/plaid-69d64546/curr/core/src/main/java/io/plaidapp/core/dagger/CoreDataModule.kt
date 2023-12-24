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

package io.plaidapp.core.dagger

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import io.plaidapp.core.data.ManualInjection
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Dagger module to provide core data functionality.
 */
@Module
class CoreDataModule(private val baseUrl: String) {

    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = ManualInjection.httpLoggingInterceptor

    @Provides
    fun provideOkHttpClient(): OkHttpClient = ManualInjection.okHttpClient

    @Provides
    fun provideRetrofit(): Retrofit = ManualInjection.retrofit(baseUrl)

    @Provides
    fun provideGson(): Gson = ManualInjection.gson

    @Provides
    fun provideGsonConverterFactory(): GsonConverterFactory = ManualInjection.gsonConverterFactory
}
