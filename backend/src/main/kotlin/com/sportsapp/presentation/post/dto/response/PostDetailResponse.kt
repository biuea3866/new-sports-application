package com.sportsapp.presentation.post.dto.response

import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import java.time.ZonedDateTime

data class PostDetailResponse(
    val id: Long,
    val userId: Long,
    val title: String,
    val content: String,
    val type: PostType,
    val createdAt: ZonedDateTime,
    val comments: List<CommentResponse>,
) {
    companion object {
        fun of(post: Post, comments: List<Comment>): PostDetailResponse = PostDetailResponse(
            id = post.id,
            userId = post.userId,
            title = post.title,
            content = post.content,
            type = post.type,
            createdAt = post.createdAt,
            comments = comments.map { CommentResponse.of(it) },
        )
    }
}
