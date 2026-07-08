package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.TransferHostCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransferHostUseCase(
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional
    fun execute(command: TransferHostCommand) {
        communityDomainService.transfer(command.communityId, command.requesterId, command.newHostUserId)
    }
}
