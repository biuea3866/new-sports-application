package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.repository.FeatureFlagAuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class FeatureFlagAuditLogRepositoryImpl(
    private val featureFlagAuditLogJpaRepository: FeatureFlagAuditLogJpaRepository,
) : FeatureFlagAuditLogRepository {

    override fun save(log: FeatureFlagAuditLog): FeatureFlagAuditLog =
        featureFlagAuditLogJpaRepository.save(log)

    override fun findByFlagKey(key: String, pageable: Pageable): Page<FeatureFlagAuditLog> =
        featureFlagAuditLogJpaRepository.findByFlagKey(key, pageable)
}
