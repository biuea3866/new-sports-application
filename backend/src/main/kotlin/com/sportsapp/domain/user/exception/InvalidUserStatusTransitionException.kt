package com.sportsapp.domain.user.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.user.entity.UserStatus

class InvalidUserStatusTransitionException(
    userId: Long?,
    currentStatus: UserStatus,
    targetStatus: UserStatus,
) : BusinessException(
    errorCode = "INVALID_USER_STATUS_TRANSITION",
    message = "Cannot transition User(id=$userId) from $currentStatus to $targetStatus",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
