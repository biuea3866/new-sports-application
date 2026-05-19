package com.sportsapp.domain.user.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidEmailException(email: String) : BusinessException(
    errorCode = "INVALID_EMAIL",
    message = "Invalid email format: $email"
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
