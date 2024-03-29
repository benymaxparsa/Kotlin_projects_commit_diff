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

package io.plaidapp.designernews.domain

import io.plaidapp.core.data.Result
import io.plaidapp.core.designernews.data.users.UserRepository
import io.plaidapp.core.designernews.data.users.model.User
import io.plaidapp.core.designernews.domain.model.Comment
import io.plaidapp.core.designernews.domain.model.CommentWithReplies
import io.plaidapp.core.designernews.domain.model.toComment

/**
 * Use case that builds [Comment]s based on comments with replies and users
 */
class CommentsUseCase(
    private val commentsWithRepliesUseCase: CommentsWithRepliesUseCase,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(ids: List<Long>): Result<List<Comment>> {
        // Get the comments with replies
        val commentsWithRepliesResult = commentsWithRepliesUseCase.getCommentsWithReplies(ids)
        if (commentsWithRepliesResult is Result.Error) {
            return Result.Error(commentsWithRepliesResult.exception)
        }
        val commentsWithReplies = (commentsWithRepliesResult as? Result.Success)?.data.orEmpty()
        // get the ids of the users that posted comments
        val userIds = mutableSetOf<Long>()
        createUserIds(commentsWithReplies, userIds)

        // get the users
        val usersResult = userRepository.getUsers(userIds)
        val users = if (usersResult is Result.Success) {
            usersResult.data
        } else {
            emptySet()
        }
        // create the comments based on the comments with replies and users
        val comments = createComments(commentsWithReplies, users)
        return Result.Success(comments)
    }

    private fun createUserIds(comments: List<CommentWithReplies>, userIds: MutableSet<Long>) {
        comments.forEach {
            userIds.add(it.userId)
            createUserIds(it.replies, userIds)
        }
    }

    private fun createComments(
        commentsWithReplies: List<CommentWithReplies>,
        users: Set<User>
    ): List<Comment> {
        val userMapping = users.map { it.id to it }.toMap()
        val comments = mutableListOf<Comment>()
        unnestCommentsWithReplies(commentsWithReplies, userMapping, comments)
        return comments
    }

    private fun unnestCommentsWithReplies(
        commentsWithReplies: List<CommentWithReplies>,
        users: Map<Long, User>,
        comments: MutableList<Comment>
    ) {
        commentsWithReplies.forEach {
            val user = users[it.userId]
            comments.add(it.toComment(user))
            if (it.replies.isNotEmpty()) {
                unnestCommentsWithReplies(it.replies, users, comments)
            }
        }
    }
}
