package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.dto.IssueMcpTokenCommand
import java.time.ZonedDateTime

data class IssueMcpTokenUseCaseCommand(
    val userId: Long,
    val name: String,
    val scopes: List<String>,
    val expiresAt: ZonedDateTime?,
) {
    fun toDomainCommand(): IssueMcpTokenCommand = IssueMcpTokenCommand(
        userId = userId,
        name = name,
        scopes = scopes,
        expiresAt = expiresAt,
    )
}
