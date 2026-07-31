package com.sportsapp.domain.user.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidCredentialsException : BusinessException(
    errorCode = "INVALID_CREDENTIALS",
    message = "Invalid email or password",
) {
    override val status: ErrorStatus = ErrorStatus.UNAUTHORIZED
}
