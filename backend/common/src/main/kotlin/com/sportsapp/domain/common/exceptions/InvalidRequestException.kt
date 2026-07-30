package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidRequestException(message: String) : BusinessException(
    errorCode = "INVALID_REQUEST",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
