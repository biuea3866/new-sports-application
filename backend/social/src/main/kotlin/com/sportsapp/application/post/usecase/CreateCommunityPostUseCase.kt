package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.CreateCommunityPostCommand
import com.sportsapp.application.post.dto.PostResponse
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.vo.CommunityPostContext
import com.sportsapp.domain.user.service.UserDomainService
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
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: CreateCommunityPostCommand): PostResponse {
        communityDomainService.requireActiveMember(command.communityId, command.userId)
        val community = communityDomainService.getCommunity(command.communityId, command.userId)
        val post = postDomainService.createCommunityPost(
            userId = command.userId,
            title = command.title,
            content = command.content,
            type = command.type,
            context = contextOf(command, community),
        )
        return PostResponse.of(post, authorDisplayNameOf(command.userId))
    }

    private fun contextOf(command: CreateCommunityPostCommand, community: Community): CommunityPostContext =
        CommunityPostContext(
            communityId = command.communityId,
            sportCategory = community.sportCategory,
            authorIsHost = community.isHostedBy(command.userId),
            communityIsPublic = community.isPublic(),
        )

    private fun authorDisplayNameOf(userId: Long): String =
        userDomainService.findDisplayNamesBy(listOf(userId)).of(userId)
}
