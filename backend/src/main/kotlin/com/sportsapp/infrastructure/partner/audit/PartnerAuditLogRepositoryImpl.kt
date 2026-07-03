package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import com.sportsapp.domain.partner.audit.PartnerAuditLogRepository
import org.springframework.stereotype.Repository

@Repository
class PartnerAuditLogRepositoryImpl(
    private val partnerAuditLogJpaRepository: PartnerAuditLogJpaRepository,
) : PartnerAuditLogRepository {

    override fun save(auditLog: PartnerAuditLog): PartnerAuditLog =
        partnerAuditLogJpaRepository.save(PartnerAuditLogJpaEntity.of(auditLog)).toDomain()
}
