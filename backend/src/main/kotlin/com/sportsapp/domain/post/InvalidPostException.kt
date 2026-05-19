package com.sportsapp.domain.post

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidPostException(message: String) : BusinessException(
    errorCode = "INVALID_POST",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
