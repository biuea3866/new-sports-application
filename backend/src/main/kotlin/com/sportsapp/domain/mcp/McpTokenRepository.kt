package com.sportsapp.domain.mcp

interface McpTokenRepository {
    fun save(mcpToken: McpToken): McpToken
    fun findById(id: Long): McpToken?
    fun findByTokenHash(tokenHash: String): McpToken?

    /**
     * 비정상 탐지 스케줄러용: 현재 ACTIVE 상태인 모든 토큰 ID 조회.
     */
    fun findAllActiveIds(): List<Long>
}
