package com.sportsapp.domain.mcp

import java.time.ZonedDateTime

data class IssueMcpTokenCommand(
    val userId: Long,
    val name: String,
    val scopes: List<String>,
    val expiresAt: ZonedDateTime?,
)
