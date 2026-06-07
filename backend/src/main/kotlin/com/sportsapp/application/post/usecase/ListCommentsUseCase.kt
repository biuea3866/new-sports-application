package com.sportsapp.application.post.usecase

import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCommentsUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(postId: Long, page: Int, size: Int): Page<Comment> =
        postDomainService.listComments(postId = postId, page = page, size = size)
}
