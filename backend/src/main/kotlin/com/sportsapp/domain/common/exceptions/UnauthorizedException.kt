package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedException(reason: String = "Authentication required") : BusinessException(
    errorCode = "UNAUTHORIZED",
    message = reason,
) {
    override val status: ErrorStatus = ErrorStatus.UNAUTHORIZED
}
