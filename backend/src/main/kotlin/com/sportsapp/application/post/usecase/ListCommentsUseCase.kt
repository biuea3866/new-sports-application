package com.sportsapp.application.post

import com.sportsapp.domain.post.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCommentsUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(postId: Long, page: Int, size: Int): CommentPageResponse {
        val commentPage = postDomainService.listComments(postId = postId, page = page, size = size)
        return CommentPageResponse.of(commentPage)
    }
}
