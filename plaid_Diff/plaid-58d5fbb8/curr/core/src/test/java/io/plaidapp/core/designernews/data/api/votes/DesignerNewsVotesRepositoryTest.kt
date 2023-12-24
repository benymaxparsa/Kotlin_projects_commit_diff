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

package io.plaidapp.core.designernews.data.api.votes

import io.plaidapp.core.data.Result
import io.plaidapp.core.designernews.data.api.DesignerNewsService
import io.plaidapp.core.designernews.data.api.any
import io.plaidapp.core.designernews.data.api.errorResponseBody
import io.plaidapp.core.designernews.data.api.provideFakeCoroutinesContextProvider
import kotlinx.coroutines.experimental.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import retrofit2.Response

/**
 * Test for [DesignerNewsVotesRepository] mocking all dependencies.
 */
class DesignerNewsVotesRepositoryTest {

    private val userId = 3L
    private val storyId = 1345L

    private val service = Mockito.mock(DesignerNewsService::class.java)
    private val votesRepository = DesignerNewsVotesRepository(
            service,
            provideFakeCoroutinesContextProvider()
    )

    @Test
    fun upvoteStory_whenRequestSuccessful() {
        // Given that the service responds with success
        val response = Response.success(Unit)
        Mockito.`when`(service.upvoteStoryV2(any())).thenReturn(CompletableDeferred(response))
        var result: Result<Unit>? = null

        // When upvoting a story
        votesRepository.upvoteStory(storyId, userId) { result = it }

        // Then the result is successful
        assertEquals(Result.Success(Unit), result)
    }

    @Test
    fun upvoteStory_whenRequestFailed() {
        // Given that the service responds with error
        val response = Response.error<Unit>(404, errorResponseBody)
        Mockito.`when`(service.upvoteStoryV2(any())).thenReturn(CompletableDeferred(response))
        var result: Result<Unit>? = null

        // When upvoting a story
        votesRepository.upvoteStory(storyId, userId) { result = it }

        // Then the result is error
        assertTrue(result is Result.Error)
    }
}
