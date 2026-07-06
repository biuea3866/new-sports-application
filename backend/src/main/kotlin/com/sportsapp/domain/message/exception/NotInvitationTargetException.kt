package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 초대 대상(invitee)이 아닌 사용자가 accept/reject 를 시도했을 때 발생한다 (TDD FR-12).
 */
class NotInvitationTargetException(
    userId: Long,
    invitationId: Long,
) : BusinessException(
    errorCode = "NOT_INVITATION_TARGET",
    message = "User $userId is not the target of invitation $invitationId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
