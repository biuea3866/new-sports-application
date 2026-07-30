package com.sportsapp.domain.partner.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

interface PartnerAuditLogCustomRepository {
    fun findBy(partnerId: Long, from: ZonedDateTime, to: ZonedDateTime, pageable: Pageable): Page<PartnerAuditLog>
}
