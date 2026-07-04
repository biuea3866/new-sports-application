package com.sportsapp.domain.featureflag.dto

data class ArchiveFeatureFlagCommand(
    val key: String,
    val actorUserId: Long,
)
