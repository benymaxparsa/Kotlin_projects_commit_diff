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

package io.plaidapp.core.designernews.data.api

import io.plaidapp.core.designernews.data.api.model.Comment
import io.plaidapp.core.designernews.data.api.model.CommentLinksResponse
import io.plaidapp.core.designernews.data.api.model.CommentResponse
import io.plaidapp.core.designernews.data.api.model.User
import io.plaidapp.core.designernews.domain.CommentWithReplies
import okhttp3.MediaType
import okhttp3.ResponseBody
import java.util.Date

/**
 * Test data for comments
 */

val createdDate = Date()

val user1 = User(
        id = 111L,
        firstName = "Plaicent",
        lastName = "van Plaid",
        displayName = "Plaicent van Plaid",
        portraitUrl = "www"
)

val user2 = User(
        id = 222L,
        firstName = "Plaude",
        lastName = "Pladon",
        displayName = "Plaude Pladon",
        portraitUrl = "www"
)

val users = listOf(user1, user2)

const val parentId = 1L

val links = CommentLinksResponse(userId = user1.id, story = 999L, parentComment = parentId)

val replyResponse1 = CommentResponse(
        id = 11L,
        body = "commenty comment",
        created_at = Date(),
        links = links
)

val replyWithReplies1 = CommentWithReplies(
        id = replyResponse1.id,
        parentId = replyResponse1.links.parentComment,
        body = replyResponse1.body,
        createdAt = replyResponse1.created_at,
        userId = replyResponse1.links.userId,
        storyId = replyResponse1.links.story,
        replies = emptyList()
)

val reply1 = Comment(
        id = replyResponse1.id,
        parentCommentId = parentId,
        body = replyResponse1.body,
        createdAt = replyResponse1.created_at,
        depth = replyResponse1.depth,
        upvotesCount = replyResponse1.vote_count,
        replies = emptyList(),
        userId = replyResponse1.links.userId,
        userDisplayName = user1.displayName,
        userPortraitUrl = user1.portraitUrl,
        upvoted = false
)

val reply1NoUser = Comment(
        id = replyResponse1.id,
        parentCommentId = parentId,
        body = replyResponse1.body,
        createdAt = replyResponse1.created_at,
        depth = replyResponse1.depth,
        upvotesCount = replyResponse1.vote_count,
        replies = emptyList(),
        userId = replyResponse1.links.userId,
        userDisplayName = null,
        userPortraitUrl = null,
        upvoted = false
)

val replyResponse2 = CommentResponse(
        id = 12L,
        body = "commenty comment",
        created_at = Date(),
        links = links
)

val replyWithReplies2 = CommentWithReplies(
        id = replyResponse2.id,
        parentId = replyResponse2.links.parentComment,
        body = replyResponse2.body,
        createdAt = replyResponse2.created_at,
        userId = replyResponse2.links.userId,
        storyId = replyResponse2.links.story,
        replies = emptyList()
)

val reply2 = Comment(
        id = replyResponse2.id,
        parentCommentId = parentId,
        body = replyResponse2.body,
        createdAt = replyResponse2.created_at,
        depth = replyResponse2.depth,
        upvotesCount = replyResponse2.vote_count,
        replies = emptyList(),
        userId = replyResponse2.links.userId,
        userDisplayName = user1.displayName,
        userPortraitUrl = user1.portraitUrl,
        upvoted = false
)

val repliesResponses = listOf(replyResponse1, replyResponse2)
val replies = listOf(reply1, reply2)

val parentLinks = CommentLinksResponse(
        userId = user2.id,
        story = 987L,
        parentComment = null,
        comments = arrayListOf(11L, 12L)
)

val parentCommentResponse = CommentResponse(
        id = parentId,
        body = "commenty comment",
        created_at = createdDate,
        links = parentLinks)

val parentCommentWithReplies = CommentWithReplies(
        id = parentCommentResponse.id,
        parentId = parentCommentResponse.links.parentComment,
        body = parentCommentResponse.body,
        createdAt = parentCommentResponse.created_at,
        userId = parentCommentResponse.links.userId,
        storyId = parentCommentResponse.links.story,
        replies = listOf(replyWithReplies1, replyWithReplies2)
)

val parentComment = Comment(
        id = parentCommentResponse.id,
        parentCommentId = null,
        body = parentCommentResponse.body,
        createdAt = parentCommentResponse.created_at,
        depth = parentCommentResponse.depth,
        upvotesCount = parentCommentResponse.vote_count,
        replies = replies,
        userId = user2.id,
        userDisplayName = user2.displayName,
        userPortraitUrl = user2.portraitUrl,
        upvoted = false
)

val parentCommentWithRepliesWithoutReplies = CommentWithReplies(
        id = parentCommentResponse.id,
        parentId = parentCommentResponse.links.parentComment,
        body = parentCommentResponse.body,
        createdAt = parentCommentResponse.created_at,
        userId = parentCommentResponse.links.userId,
        storyId = parentCommentResponse.links.story,
        replies = emptyList()
)

val parentCommentWithoutReplies = Comment(
        id = parentCommentResponse.id,
        parentCommentId = null,
        body = parentCommentResponse.body,
        createdAt = parentCommentResponse.created_at,
        depth = parentCommentResponse.depth,
        upvotesCount = parentCommentResponse.vote_count,
        replies = emptyList(),
        userId = user2.id,
        userDisplayName = user2.displayName,
        userPortraitUrl = user2.portraitUrl,
        upvoted = false
)

val errorResponseBody = ResponseBody.create(MediaType.parse(""), "Error")
