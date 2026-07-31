package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.service.McpTokenDomainService
import com.sportsapp.domain.mcp.entity.McpTokenStatus
import java.time.ZonedDateTime

data class IssueMcpTokenResponse(
    val tokenId: Long,
    val name: String,
    val plainToken: String,
    val status: McpTokenStatus,
    val expiresAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(result: McpTokenDomainService.IssueResult): IssueMcpTokenResponse =
            IssueMcpTokenResponse(
                tokenId = result.token.id,
                name = result.token.name,
                plainToken = result.plainToken,
                status = result.token.status,
                expiresAt = result.token.expiresAt,
                createdAt = result.token.createdAt,
            )
    }
}
