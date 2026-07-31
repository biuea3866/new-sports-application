package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.repository.McpTokenCustomRepository
import org.springframework.stereotype.Repository

@Repository
class McpTokenCustomRepositoryImpl(
    private val mcpTokenJpaRepository: McpTokenJpaRepository,
) : McpTokenCustomRepository {

    override fun findActiveByUserId(userId: Long): List<McpToken> =
        mcpTokenJpaRepository.findActiveByUserId(userId)
}
