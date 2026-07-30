package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class BusinessRuleViolationException(message: String) : BusinessException(
    errorCode = "BUSINESS_RULE_VIOLATION",
    message = message
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
