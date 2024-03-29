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

package io.plaidapp.designernews.ui.story

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import io.plaidapp.core.data.CoroutinesContextProvider
import io.plaidapp.core.designernews.data.stories.StoriesRepository
import io.plaidapp.designernews.domain.UpvoteCommentUseCase
import io.plaidapp.designernews.domain.UpvoteStoryUseCase

/**
 * Factory for creating [StoryViewModel] with args.
 */
class StoryViewModelFactory(
    private val storyId: Long,
    private val storiesRepository: StoriesRepository,
    private val upvoteStoryUseCase: UpvoteStoryUseCase,
    private val upvoteCommentUseCase: UpvoteCommentUseCase,
    private val contextProvider: CoroutinesContextProvider
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass != StoryViewModel::class.java) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        return StoryViewModel(
            storyId,
            storiesRepository,
            upvoteStoryUseCase,
            upvoteCommentUseCase,
            contextProvider
        ) as T
    }
}
