package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenRepository
import org.springframework.stereotype.Repository

@Repository
class McpTokenRepositoryImpl(
    private val mcpTokenJpaRepository: McpTokenJpaRepository,
) : McpTokenRepository {

    override fun save(mcpToken: McpToken): McpToken =
        mcpTokenJpaRepository.save(mcpToken)

    override fun findById(id: Long): McpToken? =
        mcpTokenJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByTokenHash(tokenHash: String): McpToken? =
        mcpTokenJpaRepository.findByTokenHashAndDeletedAtIsNull(tokenHash)
}
