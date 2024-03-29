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

package com.google.samples.apps.iosched.shared.domain.feed

import com.google.samples.apps.iosched.model.Moment
import com.google.samples.apps.iosched.shared.data.feed.FeedRepository
import com.google.samples.apps.iosched.shared.di.IoDispatcher
import com.google.samples.apps.iosched.shared.domain.UseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

/**
 * Loads a [Moment] corresponding to the given time passed as a parameter.
 */
open class LoadCurrentMomentUseCase @Inject constructor(
    private val repository: FeedRepository,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : UseCase<Instant, Moment?>(dispatcher) {

    override fun execute(parameters: Instant): Moment? {
        val time = ZonedDateTime.ofInstant(parameters, ZoneId.systemDefault())
        return repository.getMoments()
            .firstOrNull { it.startTime <= time && time < it.endTime }
    }
}
