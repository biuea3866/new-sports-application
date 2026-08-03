package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.CreatePostCommand
import com.sportsapp.application.post.dto.PostResponse
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePostUseCase(
    private val postDomainService: PostDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: CreatePostCommand): PostResponse {
        val post = postDomainService.createPost(
            userId = command.userId,
            title = command.title,
            content = command.content,
            type = command.type,
            sportCategory = command.sportCategory,
        )
        return PostResponse.of(post, userDomainService.findDisplayNamesBy(listOf(command.userId)).of(command.userId))
    }
}
