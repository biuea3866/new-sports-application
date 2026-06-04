package com.sportsapp.domain.mcp.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ConfirmationParamsMismatchException(token: String) : BusinessException(
    errorCode = "CONFIRMATION_PARAMS_MISMATCH",
    message = "Confirmation token params do not match the original request (token prefix: ${token.take(8)}***)",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
