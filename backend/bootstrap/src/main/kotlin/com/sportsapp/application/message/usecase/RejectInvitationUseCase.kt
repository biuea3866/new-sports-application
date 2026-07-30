package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RejectInvitationUseCase(
    private val guestInvitationDomainService: GuestInvitationDomainService,
) {
    @Transactional
    fun execute(invitationId: Long, userId: Long): RoomInvitation =
        guestInvitationDomainService.reject(invitationId = invitationId, userId = userId)
}
