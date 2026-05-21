package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class MalformedLockIdException(lockId: String) : BusinessException(
    errorCode = "SEAT_LOCK_MALFORMED",
    message = "seat lock id is malformed: $lockId",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
