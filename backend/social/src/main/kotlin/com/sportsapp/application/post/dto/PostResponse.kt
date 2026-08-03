package com.sportsapp.application.post.dto

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import java.time.ZonedDateTime

data class PostResponse(
    val id: Long,
    val userId: Long,
    /** 작성자 표시 이름. user 컨텍스트 소유 값이라 application 레이어가 조회해 채운다. */
    val authorDisplayName: String,
    val title: String,
    val type: PostType,
    val createdAt: ZonedDateTime,
    val communityId: Long?,
    val sportCategory: SportCategory?,
) {
    companion object {
        fun of(post: Post, authorDisplayName: String): PostResponse = PostResponse(
            id = post.id,
            userId = post.userId,
            authorDisplayName = authorDisplayName,
            title = post.title,
            type = post.type,
            createdAt = post.createdAt,
            communityId = post.currentCommunityId,
            sportCategory = post.currentSportCategory,
        )
    }
}
