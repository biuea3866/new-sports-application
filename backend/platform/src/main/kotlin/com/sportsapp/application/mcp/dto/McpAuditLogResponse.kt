package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.entity.McpAuditLog
import java.time.ZonedDateTime

data class McpAuditLogResponse(
    val id: Long,
    val tokenId: Long?,
    val toolName: String,
    val paramsMasked: String?,
    val statusCode: Int,
    val latencyMs: Int,
    val ipAddr: String?,
    val calledAt: ZonedDateTime,
) {
    companion object {
        fun of(auditLog: McpAuditLog): McpAuditLogResponse =
            McpAuditLogResponse(
                id = auditLog.id,
                tokenId = auditLog.tokenId,
                toolName = auditLog.toolName,
                paramsMasked = auditLog.paramsMasked,
                statusCode = auditLog.statusCode,
                latencyMs = auditLog.latencyMs,
                ipAddr = auditLog.ipAddr,
                calledAt = auditLog.calledAt,
            )
    }
}
