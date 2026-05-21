package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface McpAuditLogJpaRepository : JpaRepository<McpAuditLog, Long>, McpAuditLogQueryDslRepository {
    fun findAllByUserIdAndCalledAtBetween(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<McpAuditLog>
}
