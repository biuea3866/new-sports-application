package com.sportsapp.presentation.post.dto.response

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import java.time.ZonedDateTime

data class PostResponse(
    val id: Long,
    val userId: Long,
    val title: String,
    val type: PostType,
    val createdAt: ZonedDateTime,
    val communityId: Long?,
    val sportCategory: SportCategory?,
) {
    companion object {
        fun of(post: Post): PostResponse = PostResponse(
            id = post.id,
            userId = post.userId,
            title = post.title,
            type = post.type,
            createdAt = post.createdAt,
            communityId = post.currentCommunityId,
            sportCategory = post.currentSportCategory,
        )
    }
}
