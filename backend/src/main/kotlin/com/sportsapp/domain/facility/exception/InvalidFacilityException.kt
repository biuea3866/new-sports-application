package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidFacilityException(message: String) : BusinessException(
    errorCode = "INVALID_FACILITY",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
