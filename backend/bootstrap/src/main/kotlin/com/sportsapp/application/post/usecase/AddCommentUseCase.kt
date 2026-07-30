package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.AddCommentCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 댓글 작성 — 대상 게시글이 모임 소속이면 [CommunityDomainService.requireActiveMember]로
 * 작성 인가를 재사용한다(TDD FR-3, R1 배선점).
 */
@Service
class AddCommentUseCase(
    private val postDomainService: PostDomainService,
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional
    fun execute(command: AddCommentCommand): Comment {
        val post = postDomainService.getPost(command.postId)
        post.currentCommunityId?.let { communityDomainService.requireActiveMember(it, command.userId) }
        return postDomainService.addComment(postId = command.postId, userId = command.userId, content = command.content)
    }
}
