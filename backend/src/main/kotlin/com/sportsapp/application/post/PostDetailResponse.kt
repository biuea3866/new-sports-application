package com.sportsapp.application.post

import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostType
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
            postId = comment.post.id,
            userId = comment.userId,
            content = comment.content,
            createdAt = comment.createdAt,
        )
    }
}
