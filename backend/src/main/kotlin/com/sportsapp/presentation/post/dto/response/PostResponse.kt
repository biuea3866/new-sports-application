package com.sportsapp.application.post

import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostType
import java.time.ZonedDateTime

data class PostResponse(
    val id: Long,
    val userId: Long,
    val title: String,
    val type: PostType,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(post: Post): PostResponse = PostResponse(
            id = post.id,
            userId = post.userId,
            title = post.title,
            type = post.type,
            createdAt = post.createdAt,
        )
    }
}
