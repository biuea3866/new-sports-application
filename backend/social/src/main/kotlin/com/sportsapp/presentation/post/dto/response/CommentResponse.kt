package com.sportsapp.presentation.post.dto.response

import com.sportsapp.domain.post.entity.Comment
import java.time.ZonedDateTime

data class CommentResponse(
    val id: Long,
    val postId: Long,
    val userId: Long,
    val content: String,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(comment: Comment): CommentResponse = CommentResponse(
            id = comment.id,
            postId = comment.postId,
            userId = comment.userId,
            content = comment.content,
            createdAt = comment.createdAt,
        )
    }
}
