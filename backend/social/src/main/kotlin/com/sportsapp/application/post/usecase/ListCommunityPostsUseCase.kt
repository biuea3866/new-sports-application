package com.sportsapp.application.post.usecase

import com.sportsapp.application.common.GuestRequester
import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.application.post.dto.PostResponse
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 모임 게시글 목록 — `GET /communities/{id}/posts` (TDD "API 계약" 신규 엔드포인트, R1 배선점).
 * [CommunityDomainService.getCommunity]로 가시성을 인가한 뒤 communityId 로 필터한다.
 */
@Service
class ListCommunityPostsUseCase(
    private val postDomainService: PostDomainService,
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(
        communityId: Long,
        requesterId: Long?,
        sportCategory: SportCategory?,
        page: Int,
        size: Int,
    ): Page<PostResponse> {
        communityDomainService.getCommunity(communityId, requesterId ?: GuestRequester.ID)
        val criteria = criteriaOf(communityId, sportCategory, page, size)
        val posts = postDomainService.search(criteria.toSearchCriteria(), criteria.toPageable())
        val authorNames = userDomainService.findDisplayNamesBy(posts.content.map { it.userId })
        return posts.map { PostResponse.of(it, authorNames.of(it.userId)) }
    }

    private fun criteriaOf(
        communityId: Long,
        sportCategory: SportCategory?,
        page: Int,
        size: Int,
    ): PostCriteria = PostCriteria(
        type = null,
        userId = null,
        keyword = null,
        communityId = communityId,
        sportCategory = sportCategory,
        globalFeedOnly = false,
        page = page,
        size = size,
    )
}
