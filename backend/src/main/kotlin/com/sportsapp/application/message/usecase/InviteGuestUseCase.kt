package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.InviteGuestCommand
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InviteGuestUseCase(
    private val guestInvitationDomainService: GuestInvitationDomainService,
) {
    @Transactional
    fun execute(command: InviteGuestCommand): RoomInvitation =
        guestInvitationDomainService.invite(
            roomId = command.roomId,
            inviterUserId = command.inviterUserId,
            inviteeUserId = command.inviteeUserId,
            canSpeak = command.canSpeak,
            expiresInDays = command.expiresInDays,
        )
}
