package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.domain.mcp.entity.McpToken
import org.springframework.data.jpa.repository.JpaRepository

interface McpTokenJpaRepository : JpaRepository<McpToken, Long>, McpTokenQueryDslRepository {
    fun findByIdAndDeletedAtIsNull(id: Long): McpToken?
    fun findByTokenHashAndDeletedAtIsNull(tokenHash: String): McpToken?
}
