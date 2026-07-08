package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidProgramException(message: String) : BusinessException(
    errorCode = "INVALID_PROGRAM",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
