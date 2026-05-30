package com.sportsapp.application.mcp

data class MarkAnomalyFalsePositiveCommand(
    val anomalyEventId: Long,
    val requestUserId: Long,
    val note: String?,
)
