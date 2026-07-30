package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.entity.ApiKeyStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PartnerApiKeyJpaRepository : JpaRepository<PartnerApiKeyJpaEntity, Long> {
    fun findByPartnerIdAndStatus(partnerId: Long, status: ApiKeyStatus): PartnerApiKeyJpaEntity?
}
