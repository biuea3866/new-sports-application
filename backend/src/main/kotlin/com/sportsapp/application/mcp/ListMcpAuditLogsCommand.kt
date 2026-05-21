package com.sportsapp.application.mcp

import java.time.ZonedDateTime

data class ListMcpAuditLogsCommand(
    val userId: Long,
    val startCalledAt: ZonedDateTime,
    val endCalledAt: ZonedDateTime,
    val page: Int,
    val size: Int,
)
