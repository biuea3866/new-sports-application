package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.InvitationResponse
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RejectInvitationUseCase(
    private val guestInvitationDomainService: GuestInvitationDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(invitationId: Long, userId: Long): InvitationResponse {
        val invitation = guestInvitationDomainService.reject(invitationId = invitationId, userId = userId)
        val inviterName = userDomainService.findDisplayNamesBy(listOf(invitation.inviterUserId))
        return InvitationResponse.of(invitation, inviterName.of(invitation.inviterUserId))
    }
}
