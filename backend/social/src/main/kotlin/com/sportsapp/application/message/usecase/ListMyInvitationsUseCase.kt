package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.InvitationResponse
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 초대 수신함. 초대자 표시 이름은 user 컨텍스트가 소유하므로 message 도메인이 아니라 두 컨텍스트를
 * 모두 아는 이 application 레이어가 [UserDomainService] 로 한 번에 조회해 조합한다
 * (초대 건수만큼 단건 조회하는 N+1 을 만들지 않는다).
 */
@Service
class ListMyInvitationsUseCase(
    private val guestInvitationDomainService: GuestInvitationDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<InvitationResponse> {
        val invitations = guestInvitationDomainService.findMyPendingInvitations(userId)
        val inviterNames = userDomainService.findDisplayNamesBy(invitations.map { it.inviterUserId })
        return invitations.map { InvitationResponse.of(it, inviterNames.of(it.inviterUserId)) }
    }
}
