package com.sportsapp.domain.payment

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class PaymentNotFoundException(paymentId: Long) : BusinessException(
    errorCode = "PAYMENT_NOT_FOUND",
    message = "Payment $paymentId not found",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
