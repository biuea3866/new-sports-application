package com.sportsapp.domain.mcp.dto

import java.time.ZonedDateTime

data class IssueMcpTokenCommand(
    val userId: Long,
    val name: String,
    val scopes: List<String>,
    val expiresAt: ZonedDateTime?,
)
