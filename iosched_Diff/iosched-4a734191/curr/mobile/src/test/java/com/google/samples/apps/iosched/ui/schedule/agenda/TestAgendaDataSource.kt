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

package com.google.samples.apps.iosched.ui.schedule.agenda

import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaDataSource
import com.google.samples.apps.iosched.shared.model.Block
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

/**
 * Generates dummy agenda data to be used in tests.
 */
object TestAgendaDataSource : AgendaDataSource {

    private val time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"))

    val block = Block(
        title = "Keynote",
        type = "keynote",
        color = 0xffff00ff.toInt(),
        startTime = time1,
        endTime = time1.plusHours(1L)
    )

    override fun getAgenda() = listOf(block)
}
