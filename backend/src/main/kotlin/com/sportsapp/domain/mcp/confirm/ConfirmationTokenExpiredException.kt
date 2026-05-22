package com.sportsapp.domain.mcp.confirm

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ConfirmationTokenExpiredException(token: String) : BusinessException(
    errorCode = "CONFIRMATION_TOKEN_EXPIRED",
    message = "Confirmation token has expired or does not exist (token prefix: ${token.take(8)}***)",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
