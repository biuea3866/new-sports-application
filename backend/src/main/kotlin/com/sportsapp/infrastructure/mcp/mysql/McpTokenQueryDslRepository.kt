package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.domain.mcp.entity.McpToken

interface McpTokenQueryDslRepository {
    fun findActiveByUserId(userId: Long): List<McpToken>
    fun findAllActive(): List<McpToken>
}
