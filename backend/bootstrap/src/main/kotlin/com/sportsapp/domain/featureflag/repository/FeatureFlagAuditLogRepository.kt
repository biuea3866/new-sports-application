package com.sportsapp.domain.featureflag.repository

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface FeatureFlagAuditLogRepository {
    fun save(log: FeatureFlagAuditLog): FeatureFlagAuditLog

    /**
     * flagKey별 감사 이력을 occurred_at 내림차순으로 페이징 조회한다 (McpAuditLogRepository 선례 미러).
     */
    fun findByFlagKey(key: String, pageable: Pageable): Page<FeatureFlagAuditLog>
}
