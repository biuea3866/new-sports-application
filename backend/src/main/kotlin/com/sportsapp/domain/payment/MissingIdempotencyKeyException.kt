package com.sportsapp.domain.payment

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class MissingIdempotencyKeyException : BusinessException(
    errorCode = "MISSING_IDEMPOTENCY_KEY",
    message = "Idempotency-Key header is required",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
