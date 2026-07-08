package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.entity.McpTokenStatus
import java.time.ZonedDateTime

data class ListMcpTokensResponse(val tokens: List<McpTokenSummary>) {
    data class McpTokenSummary(
        val tokenId: Long,
        val name: String,
        val status: McpTokenStatus,
        val expiresAt: ZonedDateTime?,
        val lastUsedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
    ) {
        companion object {
            fun of(token: McpToken): McpTokenSummary = McpTokenSummary(
                tokenId = token.id,
                name = token.name,
                status = token.status,
                expiresAt = token.expiresAt,
                lastUsedAt = token.lastUsedAt,
                createdAt = token.createdAt,
            )
        }
    }

    companion object {
        fun of(tokens: List<McpToken>): ListMcpTokensResponse =
            ListMcpTokensResponse(tokens = tokens.map { McpTokenSummary.of(it) })
    }
}
