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

@file:JvmName("Injector")

package io.plaidapp.dagger

import io.plaidapp.core.dagger.DataManagerModule
import io.plaidapp.core.dagger.FilterAdapterModule
import io.plaidapp.core.dagger.OnDataLoadedModule
import io.plaidapp.core.dagger.SharedPreferencesModule
import io.plaidapp.core.data.OnDataLoadedCallback
import io.plaidapp.core.data.PlaidItem
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource
import io.plaidapp.ui.HomeActivity
import io.plaidapp.ui.PlaidApplication

/**
 * Injector for HomeActivity.
 */
fun inject(
    activity: HomeActivity,
    dataLoadedCallback: OnDataLoadedCallback<List<PlaidItem>>
) {
    DaggerHomeComponent.builder()
        .coreComponent(PlaidApplication.coreComponent(activity))
        .dataManagerModule(DataManagerModule())
        .dataLoadedModule(OnDataLoadedModule(dataLoadedCallback))
        .filterAdapterModule(FilterAdapterModule(activity))
        .homeModule(HomeModule(activity))
        .sharedPreferencesModule(
            SharedPreferencesModule(activity, LoginLocalDataSource.DESIGNER_NEWS_PREF)
        )
        .build()
        .inject(activity)
}
