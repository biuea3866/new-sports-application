package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class LockExpiredException(eventId: Long, seatId: Long) : BusinessException(
    errorCode = "SEAT_LOCK_EXPIRED",
    message = "seat lock for seat:$seatId of event:$eventId has expired",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
