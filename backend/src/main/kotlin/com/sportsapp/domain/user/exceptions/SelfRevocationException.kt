package com.sportsapp.domain.user.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SelfRevocationException : BusinessException(
    errorCode = "SELF_REVOCATION_FORBIDDEN",
    message = "Admin cannot revoke their own ADMIN role"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
