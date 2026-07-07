package com.sportsapp.application.post.usecase

import com.sportsapp.application.common.GuestRequester
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 게시글 상세 조회 — 모임 소속 게시글이면 [CommunityDomainService.getCommunity]로 가시성을
 * 재판정한다(TDD FR-2, R1 배선점). requesterId 가 없으면(비로그인) [GuestRequester.ID]
 * sentinel 을 전달해 PRIVATE 모임은 항상 비멤버로 거부되게 한다 — CommunityDomainService
 * 시그니처는 무수정으로 유지한다(TDD "CommunityDomainService 무수정").
 */
@Service
class GetPostUseCase(
    private val postDomainService: PostDomainService,
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(postId: Long, requesterId: Long? = null): Pair<Post, List<Comment>> {
        val (post, comments) = postDomainService.getDetail(postId)
        post.currentCommunityId?.let { communityDomainService.getCommunity(it, requesterId ?: GuestRequester.ID) }
        return post to comments
    }
}
