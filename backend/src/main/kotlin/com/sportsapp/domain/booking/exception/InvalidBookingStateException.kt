package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidBookingStateException(
    from: BookingStatus,
    to: BookingStatus,
) : BusinessException(
    errorCode = "INVALID_BOOKING_STATE",
    message = "Cannot transit booking from $from to $to"
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
