package com.sportsapp.application.post.usecase

import com.sportsapp.application.common.GuestRequester
import com.sportsapp.application.post.dto.CommentResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.service.CommentDomainService
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 댓글 목록 조회 — 대상 게시글이 모임 소속이면 [CommunityDomainService.getCommunity]로
 * 가시성을 재판정한다(TDD FR-2, R1 배선점). requesterId 부재는 [GetPostUseCase]와 동일한
 * 게스트 sentinel 규칙을 따른다.
 */
@Service
class ListCommentsUseCase(
    private val postDomainService: PostDomainService,
    private val commentDomainService: CommentDomainService,
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(postId: Long, requesterId: Long? = null, page: Int, size: Int): Page<CommentResponse> {
        // Post 삭제 여부는 검증하지 않는다(소프트 삭제 Post 의 댓글도 조회 가능). 모임 소속이면
        // 가시성만 재판정하고, Post 가 없거나 삭제됐으면 재판정을 건너뛴다.
        postDomainService.findPost(postId)?.currentCommunityId
            ?.let { communityDomainService.getCommunity(it, requesterId ?: GuestRequester.ID) }
        val comments = commentDomainService.listComments(postId = postId, page = page, size = size)
        val authorNames = userDomainService.findDisplayNamesBy(comments.content.map { it.userId })
        return comments.map { CommentResponse.of(it, authorNames.of(it.userId)) }
    }
}
