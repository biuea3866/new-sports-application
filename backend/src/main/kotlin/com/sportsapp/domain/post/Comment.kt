package com.sportsapp.domain.post

import java.time.ZonedDateTime

class Comment private constructor(
    val id: String?,
    val postId: String,
    val content: String,
    val userId: Long,
    val writer: String,
    val createdAt: ZonedDateTime,
) {

    companion object {
        private const val MAX_CONTENT_LENGTH = 1000

        fun create(
            postId: String,
            content: String,
            userId: Long,
            writer: String,
            createdAt: ZonedDateTime,
        ): Comment {
            if (content.length > MAX_CONTENT_LENGTH) throw CommentTooLongException(content.length)
            if (content.isBlank()) throw InvalidPostException("Comment content must not be blank")
            return Comment(
                id = null,
                postId = postId,
                content = content,
                userId = userId,
                writer = writer,
                createdAt = createdAt,
            )
        }

        fun reconstitute(
            id: String,
            postId: String,
            content: String,
            userId: Long,
            writer: String,
            createdAt: ZonedDateTime,
        ): Comment = Comment(
            id = id,
            postId = postId,
            content = content,
            userId = userId,
            writer = writer,
            createdAt = createdAt,
        )
    }
}
