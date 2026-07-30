package com.sportsapp.application.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import java.time.ZonedDateTime

data class FeatureFlagAuditLogResponse(
    val changeType: FeatureFlagChangeType,
    val actorUserId: Long,
    val before: FeatureFlagSnapshot?,
    val after: FeatureFlagSnapshot,
    val occurredAt: ZonedDateTime,
) {
    companion object {
        fun of(auditLog: FeatureFlagAuditLog): FeatureFlagAuditLogResponse = FeatureFlagAuditLogResponse(
            changeType = auditLog.changeType,
            actorUserId = auditLog.actorUserId,
            before = auditLog.beforeSnapshot,
            after = auditLog.afterSnapshot,
            occurredAt = auditLog.occurredAt,
        )
    }
}
