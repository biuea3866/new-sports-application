package com.sportsapp.domain.booking

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class RefundPolicyViolationException(
    bookingId: Long,
    currentStatus: BookingStatus,
) : BusinessException(
    errorCode = "REFUND_POLICY_VIOLATION",
    message = "Booking $bookingId 은(는) $currentStatus 상태이므로 환불할 수 없습니다."
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
