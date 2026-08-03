package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.AddCommentCommand
import com.sportsapp.application.post.dto.CommentResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.service.CommentDomainService
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 댓글 작성 — 대상 게시글이 모임 소속이면 [CommunityDomainService.requireActiveMember]로
 * 작성 인가를 재사용한다(TDD FR-3, R1 배선점).
 */
@Service
class AddCommentUseCase(
    private val postDomainService: PostDomainService,
    private val commentDomainService: CommentDomainService,
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: AddCommentCommand): CommentResponse {
        val post = postDomainService.getPost(command.postId)
        post.currentCommunityId?.let { communityDomainService.requireActiveMember(it, command.userId) }
        val comment = commentDomainService.addComment(
            postId = command.postId,
            userId = command.userId,
            content = command.content,
        )
        return CommentResponse.of(comment, authorDisplayNameOf(command.userId))
    }

    private fun authorDisplayNameOf(userId: Long): String =
        userDomainService.findDisplayNamesBy(listOf(userId)).of(userId)
}
