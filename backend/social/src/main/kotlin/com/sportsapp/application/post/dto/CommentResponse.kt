package com.sportsapp.application.post.dto

import com.sportsapp.domain.post.entity.Comment
import java.time.ZonedDateTime

data class CommentResponse(
    val id: Long,
    val postId: Long,
    val userId: Long,
    /** 댓글 작성자 표시 이름. user 컨텍스트 소유 값이라 application 레이어가 조회해 채운다. */
    val authorDisplayName: String,
    val content: String,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(comment: Comment, authorDisplayName: String): CommentResponse = CommentResponse(
            id = comment.id,
            postId = comment.postId,
            userId = comment.userId,
            authorDisplayName = authorDisplayName,
            content = comment.content,
            createdAt = comment.createdAt,
        )
    }
}
