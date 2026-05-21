package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenDomainService
import com.sportsapp.domain.mcp.McpTokenStatus
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
