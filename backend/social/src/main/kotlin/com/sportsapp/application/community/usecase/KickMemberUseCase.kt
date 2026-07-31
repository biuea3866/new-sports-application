package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.KickMemberCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KickMemberUseCase(
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional
    fun execute(command: KickMemberCommand) {
        communityDomainService.kick(command.communityId, command.requesterId, command.targetUserId)
    }
}
