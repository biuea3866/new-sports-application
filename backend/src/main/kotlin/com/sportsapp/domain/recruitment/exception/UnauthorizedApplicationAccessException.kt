package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedApplicationAccessException(applicationId: Long) : BusinessException(
    errorCode = "APPLICATION_ACCESS_DENIED",
    message = "Access to application $applicationId is denied",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
