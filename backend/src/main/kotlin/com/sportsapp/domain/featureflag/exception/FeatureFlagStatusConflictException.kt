package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus

class FeatureFlagStatusConflictException(
    flagKey: String,
    currentStatus: FeatureFlagStatus,
) : BusinessException(
    errorCode = "FEATURE_FLAG_STATUS_CONFLICT",
    message = "FeatureFlag(key=$flagKey) cannot be modified from status=$currentStatus",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
