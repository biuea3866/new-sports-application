package com.sportsapp.infrastructure.partner.audit

import org.springframework.data.jpa.repository.JpaRepository

interface PartnerAuditLogJpaRepository : JpaRepository<PartnerAuditLogJpaEntity, Long>
