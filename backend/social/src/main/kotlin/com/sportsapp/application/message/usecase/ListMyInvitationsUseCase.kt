package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyInvitationsUseCase(
    private val guestInvitationDomainService: GuestInvitationDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<RoomInvitation> =
        guestInvitationDomainService.findMyPendingInvitations(userId)
}
