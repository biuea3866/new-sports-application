package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.message.vo.InvitationStatus

/**
 * 이미 종료(terminal)된 초대에 재전이를 시도했을 때 발생한다 (TDD "상태 전이 표 — RoomInvitation").
 */
class InvitationNotTransitionableException(
    invitationId: Long,
    currentStatus: InvitationStatus,
    targetStatus: InvitationStatus,
) : BusinessException(
    errorCode = "INVITATION_NOT_TRANSITIONABLE",
    message = "Invitation $invitationId cannot transition from $currentStatus to $targetStatus",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
