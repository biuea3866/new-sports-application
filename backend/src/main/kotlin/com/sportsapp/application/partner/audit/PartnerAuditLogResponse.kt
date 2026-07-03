package com.sportsapp.application.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

data class PartnerAuditLogResponse(
    val id: Long?,
    val partnerId: Long,
    val userId: Long,
    val httpMethod: String,
    val requestPath: String,
    val targetResource: String?,
    val statusCode: Int,
    val latencyMs: Int,
    val calledAt: ZonedDateTime,
) {
    companion object {
        fun from(auditLog: PartnerAuditLog): PartnerAuditLogResponse = PartnerAuditLogResponse(
            id = auditLog.id,
            partnerId = auditLog.partnerId,
            userId = auditLog.userId,
            httpMethod = auditLog.httpMethod,
            requestPath = auditLog.requestPath,
            targetResource = auditLog.targetResource,
            statusCode = auditLog.statusCode,
            latencyMs = auditLog.latencyMs,
            calledAt = auditLog.calledAt,
        )

        fun from(auditLogs: Page<PartnerAuditLog>): Page<PartnerAuditLogResponse> = auditLogs.map { from(it) }
    }
}
