package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenCustomRepository
import org.springframework.stereotype.Repository

@Repository
class McpTokenCustomRepositoryImpl(
    private val mcpTokenJpaRepository: McpTokenJpaRepository,
) : McpTokenCustomRepository {

    override fun findActiveByUserId(userId: Long): List<McpToken> =
        mcpTokenJpaRepository.findActiveByUserId(userId)
}
