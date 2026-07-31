package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.CreateCommunityPostCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 모임 소속 게시글 작성 — R1 배선점 (TDD "R1 배선"). post·community 인가 오케스트레이션은
 * 여기(application)에서만 이뤄지고, domain.post 는 domain.community 를 참조하지 않는다.
 */
@Service
class CreateCommunityPostUseCase(
    private val postDomainService: PostDomainService,
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional
    fun execute(command: CreateCommunityPostCommand): Post {
        communityDomainService.requireActiveMember(command.communityId, command.userId)
        val community = communityDomainService.getCommunity(command.communityId, command.userId)
        return postDomainService.createCommunityPost(
            userId = command.userId,
            title = command.title,
            content = command.content,
            type = command.type,
            communityId = command.communityId,
            sportCategory = community.sportCategory,
            authorIsHost = community.isHostedBy(command.userId),
            communityIsPublic = community.isPublic(),
        )
    }
}
