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

import com.google.samples.apps.sunflower.utilities.runOnIoThread
import java.util.Calendar

class GardenPlantingRepository private constructor(
        private val gardenPlantingDao: GardenPlantingDao
) {

    fun createGardenPlanting(plantId: String) {
        runOnIoThread {
            // TODO remove gardenPlantingId from constructor once ID is auto-generated
            val gardenPlanting = GardenPlanting("gp$plantId", plantId)
            gardenPlantingDao.insertGardenPlanting(gardenPlanting)
        }
    }

    fun getGardenPlantingForPlant(plantId: String) =
            gardenPlantingDao.getGardenPlantingForPlant(plantId)

    companion object {

        // For Singleton instantiation
        @Volatile private var instance: GardenPlantingRepository? = null

        @JvmStatic fun getInstance(gardenPlantingDao: GardenPlantingDao) =
                instance ?: synchronized(this) {
                    instance ?: GardenPlantingRepository(gardenPlantingDao).also { instance = it }
                }
    }
}