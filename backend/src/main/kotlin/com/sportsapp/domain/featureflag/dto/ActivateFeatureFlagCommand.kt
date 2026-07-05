package com.sportsapp.domain.featureflag.dto

data class ActivateFeatureFlagCommand(
    val key: String,
    val actorUserId: Long,
)
