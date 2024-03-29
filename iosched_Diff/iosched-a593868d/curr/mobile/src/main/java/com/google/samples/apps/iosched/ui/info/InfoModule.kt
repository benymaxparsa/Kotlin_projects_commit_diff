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

package com.google.samples.apps.iosched.ui.info

import com.google.samples.apps.iosched.shared.di.FragmentScoped
import dagger.Module
import dagger.android.AndroidInjector
import dagger.android.ContributesAndroidInjector

/**
 * Module where classes needed to create the [InfoFragment] are defined.
 */
@Module
internal abstract class InfoModule {
    /**
     * Generates an [AndroidInjector] for the [InfoFragment].
     */
    @FragmentScoped
    @ContributesAndroidInjector(modules = [InfoFragmentsModule::class])
    internal abstract fun contributeInfoFragment(): InfoFragment


}
