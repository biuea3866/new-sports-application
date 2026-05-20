package com.sportsapp.domain.booking

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidSlotException(message: String) : BusinessException(
    errorCode = "INVALID_SLOT",
    message = message
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
