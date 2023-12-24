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

package io.plaidapp.core.designernews.domain

import io.plaidapp.core.data.Result
import io.plaidapp.core.designernews.data.api.DesignerNewsService
import io.plaidapp.core.designernews.data.api.errorResponseBody
import io.plaidapp.core.designernews.data.api.model.Comment
import io.plaidapp.core.designernews.data.api.model.CommentResponse
import io.plaidapp.core.designernews.data.api.model.User
import io.plaidapp.core.designernews.data.api.parentComment
import io.plaidapp.core.designernews.data.api.parentCommentResponse
import io.plaidapp.core.designernews.data.api.parentCommentWithoutReplies
import io.plaidapp.core.designernews.data.api.provideFakeCoroutinesContextProvider
import io.plaidapp.core.designernews.data.api.repliesResponses
import io.plaidapp.core.designernews.data.api.reply1
import io.plaidapp.core.designernews.data.api.reply1NoUser
import io.plaidapp.core.designernews.data.api.replyResponse1
import io.plaidapp.core.designernews.data.api.user1
import io.plaidapp.core.designernews.data.api.user2
import io.plaidapp.core.designernews.data.comments.CommentsRepository
import io.plaidapp.core.designernews.data.comments.DesignerNewsCommentsRemoteDataSource
import io.plaidapp.core.designernews.data.users.UserRepository
import io.plaidapp.core.designernews.provideCommentsUseCase
import io.plaidapp.core.designernews.provideCommentsWithRepliesUseCase
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import retrofit2.Response

/**
 * Integration test for [CommentsUseCase] where only the responses from the [DesignerNewsService]
 * are mocked. Everything else is using real implementation.
 */
class CommentsUseCaseIntegrationTest {
    private val service = Mockito.mock(DesignerNewsService::class.java)
    private val dataSource = DesignerNewsCommentsRemoteDataSource(service)
    private val commentsRepository = CommentsRepository(dataSource)
    private val userRepository = UserRepository(service)
    private val repository = provideCommentsUseCase(
            provideCommentsWithRepliesUseCase(commentsRepository),
            userRepository,
            provideFakeCoroutinesContextProvider()
    )

    @Test
    fun getComments_noReplies_whenCommentsAnUserRequestsSuccessful() = runBlocking {
        // Given that the comments request responds with success
        withComments(replyResponse1, "11")
        // Given that the user request responds with success
        withUsers(listOf(user1), "111")
        var result: Result<List<Comment>>? = null

        // When getting the replies
        repository.getComments(listOf(11L)) { it -> result = it }

        // Then the correct list of comments was requested from the API
        Mockito.verify(service).getComments("11")
        // Then the correct list is received
        assertEquals(Result.Success(listOf(reply1)), result)
    }

    @Test
    fun getComments_noReplies_whenCommentsRequestFailed() {
        // Given that the service responds with failure
        val apiResult = Response.error<List<CommentResponse>>(400, errorResponseBody)
        Mockito.`when`(service.getComments("11")).thenReturn(CompletableDeferred(apiResult))
        var result: Result<List<Comment>>? = null

        // When getting the comments
        repository.getComments(listOf(11L)) { it -> result = it }

        // Then the result is not successful
        assertNotNull(result)
        assertTrue(result is Result.Error)
    }

    @Test
    fun getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() = runBlocking {
        // Given that:
        // When requesting replies for ids 1 from service we get the parent comment but
        // without replies embedded (since that's what the next call is doing)
        withComments(parentCommentResponse, "1")
        // When requesting replies for ids 11 and 12 from service we get the children
        withComments(repliesResponses, "11,12")
        // When the user request responds with success
        withUsers(listOf(user1, user2), "222,111")
        var result: Result<List<Comment>>? = null

        // When getting the comments from the repository
        repository.getComments(listOf(1L)) { it -> result = it }

        // Then  API requests were triggered
        Mockito.verify(service).getComments("1")
        Mockito.verify(service).getComments("11,12")
        // Then the correct result is received
        assertEquals(Result.Success(listOf(parentComment)), result)
    }

    @Test
    fun getComments_multipleReplies_whenRepliesRequestFailed() = runBlocking {
        // Given that
        // When requesting replies for ids 1 from service we get the parent comment
        withComments(parentCommentResponse, "1")
        // When requesting replies for ids 11 and 12 from service we get an error
        val resultChildrenError = Response.error<List<CommentResponse>>(400, errorResponseBody)
        Mockito.`when`(service.getComments("11,12"))
                .thenReturn(CompletableDeferred(resultChildrenError))
        // Given that the user request responds with success
        withUsers(listOf(user2), "222")
        var result: Result<List<Comment>>? = null

        // When getting the comments from the repository
        repository.getComments(listOf(1L)) { it -> result = it }

        // Then  API requests were triggered
        Mockito.verify(service).getComments("1")
        Mockito.verify(service).getComments("11,12")
        // Then the correct result is received
        assertEquals(Result.Success(arrayListOf(parentCommentWithoutReplies)), result)
    }

    @Test
    fun getComments_whenUserRequestFailed() = runBlocking {
        // Given that:
        // When requesting replies for ids 1 from service we get the parent comment but
        // without replies embedded (since that's what the next call is doing)
        withComments(replyResponse1, "11")
        // Given that the user request responds with failure
        val userError = Response.error<List<User>>(400, errorResponseBody)
        Mockito.`when`(service.getUsers("111"))
                .thenReturn(CompletableDeferred(userError))
        var result: Result<List<Comment>>? = null

        // When getting the comments from the repository
        repository.getComments(listOf(11L)) { it -> result = it }

        // Then  API requests were triggered
        Mockito.verify(service).getComments("11")
        // Then the correct result is received
        assertEquals(Result.Success(arrayListOf(reply1NoUser)), result)
    }

    // Given that the users request responds with success
    private fun withUsers(users: List<User>, ids: String) = runBlocking {
        val userResult = Response.success(users)
        Mockito.`when`(service.getUsers(ids)).thenReturn(CompletableDeferred(userResult))
    }

    private fun withComments(commentResponse: CommentResponse, ids: String) {
        val resultParent = Response.success(listOf(commentResponse))
        Mockito.`when`(service.getComments(ids)).thenReturn(CompletableDeferred(resultParent))
    }

    private fun withComments(commentResponse: List<CommentResponse>, ids: String) {
        val resultParent = Response.success(commentResponse)
        Mockito.`when`(service.getComments(ids)).thenReturn(CompletableDeferred(resultParent))
    }
}
