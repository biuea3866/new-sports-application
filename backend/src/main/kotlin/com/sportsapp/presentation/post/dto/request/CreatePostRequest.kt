package com.sportsapp.presentation.post.dto.request

import com.sportsapp.application.post.dto.CreateCommunityPostCommand
import com.sportsapp.application.post.dto.CreatePostCommand
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.vo.PostType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * `POST /posts` 요청 (TDD "API 계약"). `communityId` 유무로 전역/모임 게시글을 분기한다.
 * 모임 게시글은 [sportCategory] 를 무시하고 소속 모임 값을 상속한다(FR-5) — [toCommunityCommand]
 * 는 그래서 sportCategory 를 넘기지 않는다.
 */
data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 10000)
    val content: String,

    val type: PostType = PostType.FREE,
    val communityId: Long? = null,
    val sportCategory: SportCategory? = null,
) {
    fun toCommand(userId: Long): CreatePostCommand = CreatePostCommand(
        userId = userId,
        title = title,
        content = content,
        type = type,
        sportCategory = sportCategory,
    )

    fun toCommunityCommand(userId: Long): CreateCommunityPostCommand = CreateCommunityPostCommand(
        userId = userId,
        title = title,
        content = content,
        type = type,
        communityId = requireNotNull(communityId) { "communityId must not be null for a community post" },
    )
}
