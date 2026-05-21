package com.sportsapp.presentation.mcp.admin

import com.sportsapp.application.mcp.IssueMcpTokenUseCaseCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.ZonedDateTime

data class IssueMcpTokenRequest(
    @field:NotBlank
    val name: String,
    @field:NotEmpty
    val scopes: List<String>,
    val expiresAt: ZonedDateTime?,
) {
    fun toCommand(userId: Long): IssueMcpTokenUseCaseCommand = IssueMcpTokenUseCaseCommand(
        userId = userId,
        name = name,
        scopes = scopes,
        expiresAt = expiresAt,
    )
}
