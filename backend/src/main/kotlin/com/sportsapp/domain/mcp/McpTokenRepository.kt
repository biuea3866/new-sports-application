package com.sportsapp.domain.mcp

interface McpTokenRepository {
    fun save(mcpToken: McpToken): McpToken
    fun findById(id: Long): McpToken?
    fun findByTokenHash(tokenHash: String): McpToken?
}
