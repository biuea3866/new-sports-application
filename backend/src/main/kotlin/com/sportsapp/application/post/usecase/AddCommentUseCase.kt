package com.sportsapp.application.post

import com.sportsapp.domain.post.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AddCommentUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional
    fun execute(command: AddCommentCommand): CommentResponse {
        val comment = postDomainService.addComment(
            postId = command.postId,
            userId = command.userId,
            content = command.content,
        )
        return CommentResponse.of(comment)
    }
}
