package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.domain.mcp.entity.McpTokenScope
import com.sportsapp.domain.mcp.repository.McpTokenScopeRepository
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
