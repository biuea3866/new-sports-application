package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityBookingResponse
import com.sportsapp.application.community.dto.LinkCommunityBookingCommand
import com.sportsapp.domain.community.service.CommunityBookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LinkCommunityBookingUseCase(
    private val communityBookingDomainService: CommunityBookingDomainService,
) {
    @Transactional
    fun execute(command: LinkCommunityBookingCommand): CommunityBookingResponse {
        val booking = communityBookingDomainService.link(
            communityId = command.communityId,
            hostUserId = command.hostUserId,
            slotId = command.slotId,
        )
        return CommunityBookingResponse.of(booking)
    }
}
