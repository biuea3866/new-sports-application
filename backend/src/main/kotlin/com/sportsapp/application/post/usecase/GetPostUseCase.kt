package com.sportsapp.application.post

import com.sportsapp.domain.post.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPostUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(postId: Long): PostDetailResponse {
        val (post, comments) = postDomainService.getDetail(postId)
        return PostDetailResponse.of(post, comments)
    }
}
