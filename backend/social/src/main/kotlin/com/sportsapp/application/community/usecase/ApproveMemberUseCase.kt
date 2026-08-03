package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.ApproveMemberCommand
import com.sportsapp.application.community.dto.CommunityMemberResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApproveMemberUseCase(
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: ApproveMemberCommand): CommunityMemberResponse {
        val member = communityDomainService.approve(command.communityId, command.requesterId, command.targetUserId)
        val memberNames = userDomainService.findDisplayNamesBy(listOf(command.targetUserId))
        return CommunityMemberResponse.of(member, memberNames.of(command.targetUserId))
    }
}
