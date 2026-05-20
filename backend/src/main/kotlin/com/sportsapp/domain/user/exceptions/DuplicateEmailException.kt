package com.sportsapp.domain.user.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class DuplicateEmailException(email: String) : BusinessException(
    errorCode = "DUPLICATE_EMAIL",
    message = "Email already registered: $email"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
