package com.sportsapp.domain.mcp.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.mcp.entity.McpAuditLog
import java.time.ZonedDateTime

interface McpAuditLogRepository {
    fun save(auditLog: McpAuditLog): McpAuditLog
    fun findByUserIdAndCalledAtBetween(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<McpAuditLog>
}
