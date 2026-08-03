package com.sportsapp.application.post.dto

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import com.sportsapp.domain.user.dto.UserDisplayNames
import java.time.ZonedDateTime

data class PostDetailResponse(
    val id: Long,
    val userId: Long,
    /** 작성자 표시 이름. user 컨텍스트 소유 값이라 application 레이어가 조회해 채운다. */
    val authorDisplayName: String,
    val title: String,
    val content: String,
    val type: PostType,
    val createdAt: ZonedDateTime,
    val comments: List<CommentResponse>,
    val communityId: Long?,
    val sportCategory: SportCategory?,
) {
    companion object {
        fun of(post: Post, comments: List<Comment>, displayNames: UserDisplayNames): PostDetailResponse =
            PostDetailResponse(
                id = post.id,
                userId = post.userId,
                authorDisplayName = displayNames.of(post.userId),
                title = post.title,
                content = post.content,
                type = post.type,
                createdAt = post.createdAt,
                comments = comments.map { CommentResponse.of(it, displayNames.of(it.userId)) },
                communityId = post.currentCommunityId,
                sportCategory = post.currentSportCategory,
            )
    }
}
