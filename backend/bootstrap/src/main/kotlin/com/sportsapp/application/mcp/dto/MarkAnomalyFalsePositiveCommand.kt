package com.sportsapp.application.mcp.dto

data class MarkAnomalyFalsePositiveCommand(
    val anomalyEventId: Long,
    val requestUserId: Long,
    val note: String?,
)
