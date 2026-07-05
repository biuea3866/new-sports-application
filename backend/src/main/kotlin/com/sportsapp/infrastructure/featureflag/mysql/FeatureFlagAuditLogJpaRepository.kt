package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FeatureFlagAuditLogJpaRepository : JpaRepository<FeatureFlagAuditLog, Long> {
    fun findByFlagKey(flagKey: String, pageable: Pageable): Page<FeatureFlagAuditLog>
}
