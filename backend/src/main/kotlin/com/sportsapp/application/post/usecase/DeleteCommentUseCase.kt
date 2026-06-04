package com.sportsapp.application.post

import com.sportsapp.domain.post.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteCommentUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional
    fun execute(command: DeleteCommentCommand) {
        postDomainService.deleteComment(
            commentId = command.commentId,
            requestUserId = command.requestUserId,
        )
    }
}
