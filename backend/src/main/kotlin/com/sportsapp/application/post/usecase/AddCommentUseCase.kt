package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.AddCommentCommand
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AddCommentUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional
    fun execute(command: AddCommentCommand): Comment =
        postDomainService.addComment(
            postId = command.postId,
            userId = command.userId,
            content = command.content,
        )
}
