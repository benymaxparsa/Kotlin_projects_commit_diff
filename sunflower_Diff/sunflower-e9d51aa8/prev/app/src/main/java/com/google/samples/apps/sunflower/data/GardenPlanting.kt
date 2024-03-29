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

package com.google.samples.apps.sunflower.data

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "garden_plantings", foreignKeys = [ForeignKey(entity = Plant::class,
        parentColumns = ["id"], childColumns = ["plant_id"])])
data class GardenPlanting(
        @PrimaryKey @ColumnInfo(name = "id") val gardenPlantingId: String,
        @ColumnInfo(name = "plant_id") val plantId: String,
        val plantDate: Calendar = Calendar.getInstance(),
        val lastWateringDate: Calendar = Calendar.getInstance()
)