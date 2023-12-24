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

package io.plaidapp.search.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.plaidapp.core.data.CoroutinesDispatcherProvider
import io.plaidapp.core.feed.FeedProgressUiModel
import io.plaidapp.core.feed.FeedUiModel
import io.plaidapp.search.domain.SearchDataSourceFactoriesRegistry
import io.plaidapp.search.domain.SearchUseCase
import kotlinx.coroutines.launch

/**
 * [ViewModel] for the [SearchActivity]. Works with the data manager to load data and prepares it
 * for display in the [SearchActivity].
 */
class SearchViewModel(
    sourcesRegistry: SearchDataSourceFactoriesRegistry,
    private val dispatcherProvider: CoroutinesDispatcherProvider
) : ViewModel() {

    private val factories = sourcesRegistry.dataSourceFactories

    private var searchUseCase: SearchUseCase? = null

    private val searchQuery = MutableLiveData<String>()

    private val results = Transformations.switchMap(searchQuery) {
        searchUseCase = SearchUseCase(factories, it)
        loadMore()
        return@switchMap searchUseCase?.searchResult
    }

    val searchResults: LiveData<FeedUiModel> = Transformations.map(results) {
        FeedUiModel(it)
    }

    private val _searchProgress = MutableLiveData<FeedProgressUiModel>()
    val searchProgress: LiveData<FeedProgressUiModel>
        get() = _searchProgress

    fun searchFor(query: String) {
        searchQuery.postValue(query)
    }

    fun loadMore() = viewModelScope.launch(dispatcherProvider.computation) {
        _searchProgress.postValue(FeedProgressUiModel(true))
        searchUseCase?.loadMore()
        _searchProgress.postValue(FeedProgressUiModel(false))
    }

    fun clearResults() {
        searchUseCase = null
    }
}
