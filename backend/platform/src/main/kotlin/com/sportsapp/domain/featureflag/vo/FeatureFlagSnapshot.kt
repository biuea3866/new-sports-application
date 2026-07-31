package com.sportsapp.domain.featureflag.vo

import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy

/**
 * 캐시(Redis)·감사 로그가 공유하는 플래그 값 스냅샷.
 *
 * raw String/Map이 아닌 타입화된 data class로 보유한다 (private-be-code-convention no-stringified-json).
 */
data class FeatureFlagSnapshot(
    val key: String,
    val type: FeatureFlagType,
    val status: FeatureFlagStatus,
    val strategy: EvaluationStrategy,
    val description: String?,
)
