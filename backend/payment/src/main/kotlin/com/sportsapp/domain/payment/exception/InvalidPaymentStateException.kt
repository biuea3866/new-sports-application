package com.sportsapp.domain.payment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.payment.entity.PaymentStatus

class InvalidPaymentStateException(
    from: PaymentStatus,
    to: PaymentStatus,
) : BusinessException(
    errorCode = "INVALID_PAYMENT_STATE",
    message = "Cannot transit payment status from $from to $to",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
