package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class FeatureFlagNotFoundException(flagKey: String) : BusinessException(
    errorCode = "FEATURE_FLAG_NOT_FOUND",
    message = "FeatureFlag(key=$flagKey) not found",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
