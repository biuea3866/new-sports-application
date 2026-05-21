package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpToken
import org.springframework.data.jpa.repository.JpaRepository

interface McpTokenJpaRepository : JpaRepository<McpToken, Long>, McpTokenQueryDslRepository {
    fun findByTokenHashAndDeletedAtIsNull(tokenHash: String): McpToken?
}
