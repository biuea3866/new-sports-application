package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.InviteGuestCommand
import com.sportsapp.application.message.dto.InvitationResponse
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InviteGuestUseCase(
    private val guestInvitationDomainService: GuestInvitationDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: InviteGuestCommand): InvitationResponse {
        val result = guestInvitationDomainService.invite(
            roomId = command.roomId,
            inviterUserId = command.inviterUserId,
            inviteeUserId = command.inviteeUserId,
            canSpeak = command.canSpeak,
            expiresInDays = command.expiresInDays,
        )
        val inviterName = userDomainService.findDisplayNamesBy(listOf(command.inviterUserId))
        return InvitationResponse.of(result.invitation, inviterName.of(command.inviterUserId), result.reused)
    }
}
