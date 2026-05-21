package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpToken

interface McpTokenQueryDslRepository {
    fun findActiveByUserId(userId: Long): List<McpToken>
}
