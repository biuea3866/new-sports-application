package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SeatAlreadyLockedException(eventId: Long, seatId: Long) : BusinessException(
    errorCode = "SEAT_ALREADY_LOCKED",
    message = "seat:$seatId of event:$eventId is already locked by another user",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
