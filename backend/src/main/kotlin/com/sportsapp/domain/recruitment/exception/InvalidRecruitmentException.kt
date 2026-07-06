package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidRecruitmentException(message: String) : BusinessException(
    errorCode = "INVALID_RECRUITMENT",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
