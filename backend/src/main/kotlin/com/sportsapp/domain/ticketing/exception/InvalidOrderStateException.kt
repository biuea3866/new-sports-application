package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidOrderStateException(message: String) : BusinessException(
    errorCode = "INVALID_ORDER_STATE",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
