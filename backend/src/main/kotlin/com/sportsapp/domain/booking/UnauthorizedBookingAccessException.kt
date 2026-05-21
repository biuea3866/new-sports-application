package com.sportsapp.domain.booking

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedBookingAccessException(bookingId: Long) : BusinessException(
    errorCode = "BOOKING_ACCESS_DENIED",
    message = "Access to booking $bookingId is denied",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
