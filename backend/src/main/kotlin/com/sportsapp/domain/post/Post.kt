package com.sportsapp.domain.post

import java.time.ZonedDateTime

class Post private constructor(
    val id: String?,
    val type: PostType,
    val title: String,
    val content: String,
    val userId: Long,
    val writer: String,
    val createdAt: ZonedDateTime,
) {

    companion object {
        private const val MAX_TITLE_LENGTH = 200
        private const val MAX_CONTENT_LENGTH = 10_000

        fun create(
            type: PostType,
            title: String,
            content: String,
            userId: Long,
            writer: String,
            createdAt: ZonedDateTime,
        ): Post {
            validateTitle(title)
            validateContent(content)
            return Post(
                id = null,
                type = type,
                title = title,
                content = content,
                userId = userId,
                writer = writer,
                createdAt = createdAt,
            )
        }

        private fun validateTitle(title: String) {
            if (title.isBlank()) throw InvalidPostException("Title must not be blank")
            if (title.length > MAX_TITLE_LENGTH) {
                throw InvalidPostException("Title length ${title.length} exceeds maximum $MAX_TITLE_LENGTH characters")
            }
        }

        private fun validateContent(content: String) {
            if (content.isBlank()) throw InvalidPostException("Content must not be blank")
            if (content.length > MAX_CONTENT_LENGTH) {
                throw InvalidPostException("Content length ${content.length} exceeds maximum $MAX_CONTENT_LENGTH characters")
            }
        }

        fun reconstitute(
            id: String,
            type: PostType,
            title: String,
            content: String,
            userId: Long,
            writer: String,
            createdAt: ZonedDateTime,
        ): Post = Post(
            id = id,
            type = type,
            title = title,
            content = content,
            userId = userId,
            writer = writer,
            createdAt = createdAt,
        )
    }

    private fun validateChangeInputs(newTitle: String, newContent: String) {
        if (newTitle.isBlank()) throw InvalidPostException("Title must not be blank")
        if (newContent.isBlank()) throw InvalidPostException("Content must not be blank")
    }

    fun changeContent(requestingUserId: Long, newTitle: String, newContent: String): Post {
        if (userId != requestingUserId) throw NotPostOwnerException(requestingUserId)
        validateChangeInputs(newTitle, newContent)
        return Post(
            id = id,
            type = type,
            title = newTitle,
            content = newContent,
            userId = userId,
            writer = writer,
            createdAt = createdAt,
        )
    }
}
