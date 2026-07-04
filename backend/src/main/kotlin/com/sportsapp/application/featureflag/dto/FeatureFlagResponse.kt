package com.sportsapp.application.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import java.time.ZonedDateTime

/**
 * 관리 API 응답 — strategy는 [EvaluationStrategy]의 `@JsonTypeInfo(property = "strategyType")`로
 * 하위 타입(GLOBAL_TOGGLE/PERCENTAGE_ROLLOUT/ATTRIBUTE_MATCH/VARIANT_BUCKETING)을 판별해 직렬화한다.
 */
data class FeatureFlagResponse(
    val id: Long,
    val key: String,
    val type: FeatureFlagType,
    val status: FeatureFlagStatus,
    val description: String?,
    val strategy: EvaluationStrategy,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        fun of(flag: FeatureFlag): FeatureFlagResponse = FeatureFlagResponse(
            id = flag.id,
            key = flag.flagKey,
            type = flag.type,
            status = flag.status,
            description = flag.description,
            strategy = flag.strategy,
            createdAt = flag.createdAt,
            updatedAt = flag.updatedAt,
        )
    }
}
