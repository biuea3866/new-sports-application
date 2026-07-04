package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class DuplicateFeatureFlagKeyException(flagKey: String) : BusinessException(
    errorCode = "DUPLICATE_FEATURE_FLAG_KEY",
    message = "FeatureFlag(key=$flagKey) already exists",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
