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

package io.plaidapp.designernews.dagger

import dagger.Module
import dagger.Provides
import io.plaidapp.core.dagger.DataSourcesModule
import io.plaidapp.core.designernews.data.stories.StoriesRepository
import io.plaidapp.core.interfaces.SearchDataSourceFactoriesRegistry
import io.plaidapp.designernews.domain.search.DesignerNewsSearchDataSourceFactory

@Module(includes = [DataSourcesModule::class])
class SearchDataModule {

    @Provides
    fun designerNewsSearchDataSourceFactory(
        repository: StoriesRepository,
        registry: SearchDataSourceFactoriesRegistry
    ): DesignerNewsSearchDataSourceFactory {
        val factory = DesignerNewsSearchDataSourceFactory(repository)
        registry.add(factory)
        return factory
    }
}
