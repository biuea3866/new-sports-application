package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidEventStateException(message: String) : BusinessException(
    errorCode = "INVALID_EVENT_STATE",
    message = message
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
