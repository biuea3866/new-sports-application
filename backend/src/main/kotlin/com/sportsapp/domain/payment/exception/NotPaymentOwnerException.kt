package com.sportsapp.domain.payment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotPaymentOwnerException(paymentId: Long) : BusinessException(
    errorCode = "NOT_PAYMENT_OWNER",
    message = "Payment $paymentId does not belong to the requesting user",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
