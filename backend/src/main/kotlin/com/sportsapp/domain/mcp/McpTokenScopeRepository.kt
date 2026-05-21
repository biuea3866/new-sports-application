package com.sportsapp.domain.mcp

interface McpTokenScopeRepository {
    fun save(mcpTokenScope: McpTokenScope): McpTokenScope
    fun findByTokenId(tokenId: Long): List<McpTokenScope>
}
