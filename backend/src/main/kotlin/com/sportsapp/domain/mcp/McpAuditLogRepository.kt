package com.sportsapp.domain.mcp

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
