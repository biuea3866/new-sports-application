package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpTokenScope
import org.springframework.data.jpa.repository.JpaRepository

interface McpTokenScopeJpaRepository : JpaRepository<McpTokenScope, Long> {
    fun findByTokenIdAndDeletedAtIsNull(tokenId: Long): List<McpTokenScope>
}
