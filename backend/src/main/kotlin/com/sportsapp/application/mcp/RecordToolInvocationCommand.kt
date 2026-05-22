package com.sportsapp.application.mcp

import java.time.ZonedDateTime

data class RecordToolInvocationCommand(
    val tokenId: Long?,
    val userId: Long,
    val toolName: String,
    val paramsMasked: String?,
    val statusCode: Int,
    val latencyMs: Int,
    val ipAddr: String?,
    val clientUserAgent: String?,
    val calledAt: ZonedDateTime,
)
