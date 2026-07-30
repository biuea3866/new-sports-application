package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class RefundBookingException(
    bookingId: Long,
    reason: String,
) : BusinessException(
    errorCode = "REFUND_BOOKING_FAILED",
    message = "Booking $bookingId 환불 실패: $reason"
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
