package com.sportsapp.domain.mcp.repository

import com.sportsapp.domain.mcp.entity.McpToken
interface McpTokenCustomRepository {
    fun findActiveByUserId(userId: Long): List<McpToken>
}
