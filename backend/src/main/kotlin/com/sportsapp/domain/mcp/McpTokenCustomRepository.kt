package com.sportsapp.domain.mcp

interface McpTokenCustomRepository {
    fun findActiveByUserId(userId: Long): List<McpToken>
}
