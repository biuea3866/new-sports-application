package com.sportsapp.domain.partner.audit

interface PartnerAuditLogRepository {
    fun save(auditLog: PartnerAuditLog): PartnerAuditLog
}
