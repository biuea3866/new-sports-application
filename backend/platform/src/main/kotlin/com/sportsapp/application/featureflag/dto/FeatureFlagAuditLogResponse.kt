package com.sportsapp.application.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import java.time.ZonedDateTime

data class FeatureFlagAuditLogResponse(
    val changeType: FeatureFlagChangeType,
    val actorUserId: Long,
    /** 변경자 표시 이름. user 컨텍스트 소유 값이라 application 레이어(UseCase)가 조회해 채운다. */
    val actorDisplayName: String,
    val before: FeatureFlagSnapshot?,
    val after: FeatureFlagSnapshot,
    val occurredAt: ZonedDateTime,
) {
    companion object {
        fun of(auditLog: FeatureFlagAuditLog, actorDisplayName: String): FeatureFlagAuditLogResponse =
            FeatureFlagAuditLogResponse(
                changeType = auditLog.changeType,
                actorUserId = auditLog.actorUserId,
                actorDisplayName = actorDisplayName,
                before = auditLog.beforeSnapshot,
                after = auditLog.afterSnapshot,
                occurredAt = auditLog.occurredAt,
            )
    }
}
