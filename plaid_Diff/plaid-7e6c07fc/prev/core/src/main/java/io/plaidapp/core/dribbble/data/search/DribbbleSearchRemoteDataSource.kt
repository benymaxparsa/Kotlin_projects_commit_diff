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

package io.plaidapp.core.dribbble.data.search

import io.plaidapp.core.data.Result
import io.plaidapp.core.dribbble.data.api.model.Shot
import io.plaidapp.core.dribbble.data.search.DribbbleSearchRemoteDataSource.SortOrder.RECENT
import io.plaidapp.core.dribbble.data.search.DribbbleSearchService.Companion.PER_PAGE_DEFAULT
import java.io.IOException

/**
 * Work with our fake Dribbble API to search for shots by query term.
 */
class DribbbleSearchRemoteDataSource(private val service: DribbbleSearchService) {

    suspend fun search(
        query: String,
        page: Int,
        sortOrder: SortOrder = RECENT,
        pageSize: Int = PER_PAGE_DEFAULT
    ): Result<List<Shot>> {
        val response = service.searchDeferred(query, page, sortOrder.sort, pageSize).await()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                return Result.Success(body)
            }
        }
        return Result.Error(
            IOException("Error getting comments ${response.code()} ${response.message()}")
        )
    }

    enum class SortOrder(val sort: String) {
        POPULAR(""),
        RECENT("latest")
    }

    companion object {
        @Volatile private var INSTANCE: DribbbleSearchRemoteDataSource? = null

        fun getInstance(service: DribbbleSearchService): DribbbleSearchRemoteDataSource {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DribbbleSearchRemoteDataSource(service).also { INSTANCE = it }
            }
        }
    }
}
