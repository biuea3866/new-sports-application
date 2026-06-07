package com.sportsapp.application.post.usecase

import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPostUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(postId: Long): Pair<Post, List<Comment>> =
        postDomainService.getDetail(postId)
}
