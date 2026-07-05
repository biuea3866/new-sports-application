package com.sportsapp.presentation.featureflag.dto

import com.sportsapp.domain.featureflag.dto.CreateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy

/**
 * 관리 API 플래그 생성 요청 — `strategy`는 [EvaluationStrategy]의 `@JsonTypeInfo(property = "strategyType")`로
 * 하위 타입(GLOBAL_TOGGLE/PERCENTAGE_ROLLOUT/ATTRIBUTE_MATCH/VARIANT_BUCKETING)을 판별해 역직렬화한다.
 */
data class CreateFeatureFlagRequest(
    val key: String,
    val type: FeatureFlagType,
    val description: String?,
    val strategy: EvaluationStrategy,
) {
    fun toCommand(actorUserId: Long): CreateFeatureFlagCommand = CreateFeatureFlagCommand(
        flagKey = key,
        type = type,
        strategy = strategy,
        description = description,
        actorUserId = actorUserId,
    )
}
