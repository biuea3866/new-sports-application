package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.domain.mcp.entity.McpTokenScope
import org.springframework.data.jpa.repository.JpaRepository

interface McpTokenScopeJpaRepository : JpaRepository<McpTokenScope, Long> {
    fun findByTokenIdAndDeletedAtIsNull(tokenId: Long): List<McpTokenScope>
}
