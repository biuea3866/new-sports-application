package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityMemberResponse
import com.sportsapp.application.community.dto.JoinCommunityCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JoinCommunityUseCase(
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: JoinCommunityCommand): CommunityMemberResponse {
        val member = communityDomainService.join(command.communityId, command.userId)
        val memberNames = userDomainService.findDisplayNamesBy(listOf(command.userId))
        return CommunityMemberResponse.of(member, memberNames.of(command.userId))
    }
}
