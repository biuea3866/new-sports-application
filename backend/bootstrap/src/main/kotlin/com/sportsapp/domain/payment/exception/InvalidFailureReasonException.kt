package com.sportsapp.domain.payment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidFailureReasonException : BusinessException(
    errorCode = "INVALID_FAILURE_REASON",
    message = "Failure reason must not be blank",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
