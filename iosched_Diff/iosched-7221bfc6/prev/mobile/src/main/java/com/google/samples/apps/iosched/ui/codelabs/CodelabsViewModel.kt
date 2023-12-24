/*
 * Copyright 2019 Google LLC
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

package com.google.samples.apps.iosched.ui.codelabs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.samples.apps.iosched.model.Codelab
import com.google.samples.apps.iosched.shared.domain.codelabs.LoadCodelabsUseCase
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.shared.result.successOr
import com.google.samples.apps.iosched.shared.util.map
import javax.inject.Inject

class CodelabsViewModel @Inject constructor(
    loadCodelabsUseCase: LoadCodelabsUseCase
) : ViewModel() {

    private val codelabsUseCaseResult = MutableLiveData<Result<List<Codelab>>>()
    val codelabs: LiveData<List<Codelab>>

    init {
        codelabs = codelabsUseCaseResult.map {
            it.successOr(emptyList())
        }

        loadCodelabsUseCase(Unit, codelabsUseCaseResult)
    }
}
