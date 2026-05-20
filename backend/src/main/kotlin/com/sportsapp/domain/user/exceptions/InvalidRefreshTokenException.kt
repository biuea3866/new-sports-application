package com.sportsapp.domain.user.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidRefreshTokenException : BusinessException(
    errorCode = "INVALID_REFRESH_TOKEN",
    message = "Refresh token is invalid or expired",
) {
    override val status: ErrorStatus = ErrorStatus.UNAUTHORIZED
}
