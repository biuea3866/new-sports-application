package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.recruitment.vo.ApplicationStatus

class InvalidApplicationStateException(
    from: ApplicationStatus,
    to: ApplicationStatus,
) : BusinessException(
    errorCode = "INVALID_APPLICATION_STATE",
    message = "Cannot transit application from $from to $to",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
