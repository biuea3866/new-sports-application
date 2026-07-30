package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SeatNotLockOwnerException(eventId: Long, seatId: Long) : BusinessException(
    errorCode = "SEAT_NOT_LOCK_OWNER",
    message = "seat:$seatId of event:$eventId is not owned by the requesting user",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
