package com.sportsapp.presentation.mcp.confirm

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ConfirmationTokenAlreadyConsumedException(token: String) : BusinessException(
    errorCode = "CONFIRMATION_TOKEN_ALREADY_CONSUMED",
    message = "Confirmation token has already been consumed (token prefix: ${token.take(8)}***)",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
