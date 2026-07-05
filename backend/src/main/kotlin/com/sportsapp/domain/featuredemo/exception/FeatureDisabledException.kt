package com.sportsapp.domain.featuredemo.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 데모 게이팅(BE-09) 플래그가 비활성(미존재·OFF·archive·롤아웃 미포함) 판정일 때 던진다.
 *
 * [ErrorStatus.SERVICE_UNAVAILABLE](503)로 매핑되어 GlobalExceptionHandler가 자동 처리한다.
 */
class FeatureDisabledException(flagKey: String) : BusinessException(
    errorCode = "FEATURE_DISABLED",
    message = "Feature(key=$flagKey) is disabled",
) {
    override val status: ErrorStatus = ErrorStatus.SERVICE_UNAVAILABLE
}
