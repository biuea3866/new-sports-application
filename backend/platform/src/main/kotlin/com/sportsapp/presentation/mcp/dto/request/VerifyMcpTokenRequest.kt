package com.sportsapp.presentation.mcp.dto.request

import com.sportsapp.application.mcp.dto.VerifyMcpTokenCommand
import jakarta.validation.constraints.NotBlank

data class VerifyMcpTokenRequest(
    @field:NotBlank
    val token: String,
) {
    fun toCommand(): VerifyMcpTokenCommand = VerifyMcpTokenCommand(plainToken = token)
}
