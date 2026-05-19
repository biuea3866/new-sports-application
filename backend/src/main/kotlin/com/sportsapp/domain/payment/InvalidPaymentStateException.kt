package com.sportsapp.domain.payment

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidPaymentStateException(
    from: PaymentStatus,
    to: PaymentStatus,
) : BusinessException(
    errorCode = "INVALID_PAYMENT_STATE",
    message = "Cannot transit payment status from $from to $to",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
