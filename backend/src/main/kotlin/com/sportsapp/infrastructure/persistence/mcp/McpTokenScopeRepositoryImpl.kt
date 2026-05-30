package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpTokenScope
import com.sportsapp.domain.mcp.McpTokenScopeRepository
import org.springframework.stereotype.Repository

@Repository
class McpTokenScopeRepositoryImpl(
    private val mcpTokenScopeJpaRepository: McpTokenScopeJpaRepository,
) : McpTokenScopeRepository {

    override fun save(mcpTokenScope: McpTokenScope): McpTokenScope =
        mcpTokenScopeJpaRepository.save(mcpTokenScope)

    override fun findByTokenId(tokenId: Long): List<McpTokenScope> =
        mcpTokenScopeJpaRepository.findByTokenIdAndDeletedAtIsNull(tokenId)
}
