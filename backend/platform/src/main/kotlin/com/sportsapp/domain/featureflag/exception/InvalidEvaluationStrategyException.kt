package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidEvaluationStrategyException(reason: String) : BusinessException(
    errorCode = "INVALID_EVALUATION_STRATEGY",
    message = reason,
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
