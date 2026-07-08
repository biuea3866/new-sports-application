package com.sportsapp.domain.featureflag.dto

import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy

data class UpdateFeatureFlagCommand(
    val key: String,
    val strategy: EvaluationStrategy,
    val description: String?,
    val actorUserId: Long,
)
