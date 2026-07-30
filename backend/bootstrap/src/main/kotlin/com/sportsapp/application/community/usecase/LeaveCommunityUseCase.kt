package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.LeaveCommunityCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeaveCommunityUseCase(
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional
    fun execute(command: LeaveCommunityCommand) {
        communityDomainService.leave(command.communityId, command.userId)
    }
}
