package com.sportsapp.application.mcp

import java.time.ZonedDateTime

data class GetMcpUsageAnalyticsCommand(
    val userId: Long,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)
