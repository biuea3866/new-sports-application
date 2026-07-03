package com.sportsapp.application.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLogDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListPartnerAuditLogsUseCase(
    private val partnerAuditLogDomainService: PartnerAuditLogDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListPartnerAuditLogsCommand): Page<PartnerAuditLogResponse> {
        val auditLogs = partnerAuditLogDomainService.listBy(
            command.partnerId,
            command.from,
            command.to,
            command.pageable,
        )
        return PartnerAuditLogResponse.from(auditLogs)
    }
}
