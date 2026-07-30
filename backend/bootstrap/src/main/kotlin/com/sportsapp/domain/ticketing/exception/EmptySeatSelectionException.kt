package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class EmptySeatSelectionException : BusinessException(
    errorCode = "EMPTY_SEAT_SELECTION",
    message = "seatIds must not be empty",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
