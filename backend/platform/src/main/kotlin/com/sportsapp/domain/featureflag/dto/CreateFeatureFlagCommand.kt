package com.sportsapp.domain.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy

data class CreateFeatureFlagCommand(
    val flagKey: String,
    val type: FeatureFlagType,
    val strategy: EvaluationStrategy,
    val description: String?,
    val actorUserId: Long,
)
