package com.sportsapp.domain.mcp

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class McpAuditLogDomainService(
    private val mcpAuditLogRepository: McpAuditLogRepository,
) {
    fun listByUser(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<McpAuditLog> =
        mcpAuditLogRepository.findByUserIdAndCalledAtBetween(userId, from, to, pageable)
}
