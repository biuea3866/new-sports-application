package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.CreatePostCommand
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePostUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional
    fun execute(command: CreatePostCommand): Post =
        postDomainService.createPost(
            userId = command.userId,
            title = command.title,
            content = command.content,
        )
}
