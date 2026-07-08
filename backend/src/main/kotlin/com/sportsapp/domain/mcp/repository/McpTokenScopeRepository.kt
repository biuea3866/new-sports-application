package com.sportsapp.domain.mcp.repository

import com.sportsapp.domain.mcp.entity.McpTokenScope
interface McpTokenScopeRepository {
    fun save(mcpTokenScope: McpTokenScope): McpTokenScope
    fun findByTokenId(tokenId: Long): List<McpTokenScope>
}
