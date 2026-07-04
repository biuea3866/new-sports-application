package com.sportsapp.presentation.featureflag.dto

import com.sportsapp.domain.featureflag.dto.UpdateFeatureFlagCommand
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy

/**
 * 관리 API 플래그 수정 요청 — `strategy`는 [EvaluationStrategy]의 `@JsonTypeInfo(property = "strategyType")`로
 * 하위 타입을 판별해 역직렬화한다.
 */
data class UpdateFeatureFlagRequest(
    val description: String?,
    val strategy: EvaluationStrategy,
) {
    fun toCommand(key: String, actorUserId: Long): UpdateFeatureFlagCommand = UpdateFeatureFlagCommand(
        key = key,
        strategy = strategy,
        description = description,
        actorUserId = actorUserId,
    )
}
