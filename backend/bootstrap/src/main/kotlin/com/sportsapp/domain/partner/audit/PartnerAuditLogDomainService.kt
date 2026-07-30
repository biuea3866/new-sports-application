package com.sportsapp.domain.partner.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class PartnerAuditLogDomainService(
    private val partnerAuditLogRepository: PartnerAuditLogRepository,
    private val partnerAuditLogCustomRepository: PartnerAuditLogCustomRepository,
) {
    fun record(auditLog: PartnerAuditLog): PartnerAuditLog =
        partnerAuditLogRepository.save(auditLog)

    fun listBy(partnerId: Long, from: ZonedDateTime, to: ZonedDateTime, pageable: Pageable): Page<PartnerAuditLog> =
        partnerAuditLogCustomRepository.findBy(partnerId, from, to, pageable)
}
