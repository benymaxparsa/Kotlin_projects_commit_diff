/*
 * Copyright 2019 Google, Inc.
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

package io.plaidapp.designernews.domain.search

import io.plaidapp.core.data.PlaidItem
import io.plaidapp.core.data.Result
import io.plaidapp.core.data.SourceItem
import io.plaidapp.core.designernews.data.stories.StoriesRepository
import io.plaidapp.core.designernews.data.stories.model.toStory
import io.plaidapp.core.interfaces.PlaidDataSource

/**
 * Data source that knows how to get designer news data for a specific source item.
 */
class DesignerNewsDataSource(
    sourceItem: SourceItem,
    private val repository: StoriesRepository
) : PlaidDataSource(sourceItem) {

    private var page = 0

    override suspend fun loadMore(): Result<List<PlaidItem>> {
        val result = repository.search(sourceItem.key, page)
        return when (result) {
            is Result.Success -> {
                page++
                val stories = result.data.map { it.toStory() }
                Result.Success(stories)
            }
            is Result.Error -> result
        }
    }
}
