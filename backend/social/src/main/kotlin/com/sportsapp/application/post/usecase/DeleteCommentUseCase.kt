package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.DeleteCommentCommand
import com.sportsapp.domain.post.service.CommentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteCommentUseCase(
    private val commentDomainService: CommentDomainService,
) {
    @Transactional
    fun execute(command: DeleteCommentCommand) {
        commentDomainService.deleteComment(
            commentId = command.commentId,
            requestUserId = command.requestUserId,
        )
    }
}
