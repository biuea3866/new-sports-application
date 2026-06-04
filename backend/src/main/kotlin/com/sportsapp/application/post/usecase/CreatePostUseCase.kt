package com.sportsapp.application.post

import com.sportsapp.domain.post.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePostUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional
    fun execute(command: CreatePostCommand): PostResponse {
        val post = postDomainService.createPost(
            userId = command.userId,
            title = command.title,
            content = command.content,
        )
        return PostResponse.of(post)
    }
}
