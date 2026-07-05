package com.sportsapp.domain.featureflag.entity

import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.hypersistence.utils.hibernate.type.json.JsonStringType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.ZonedDateTime

/**
 * 피처 플래그 변경 감사 이력 (append-only, `feature_flag_audit_logs` — V42).
 *
 * flagKey는 [after] 스냅샷의 key로부터 유도한다 — 호출부가 중복 전달하지 않는다.
 */
@Entity
@Table(name = "feature_flag_audit_logs")
class FeatureFlagAuditLog private constructor(
    @Column(name = "flag_key", nullable = false, length = 100)
    val flagKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    val changeType: FeatureFlagChangeType,

    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: Long,

    @Type(JsonStringType::class)
    @Column(name = "before_snapshot", columnDefinition = "TEXT")
    val beforeSnapshot: FeatureFlagSnapshot?,

    @Type(JsonStringType::class)
    @Column(name = "after_snapshot", nullable = false, columnDefinition = "TEXT")
    val afterSnapshot: FeatureFlagSnapshot,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: ZonedDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        fun create(
            changeType: FeatureFlagChangeType,
            actorUserId: Long,
            before: FeatureFlagSnapshot?,
            after: FeatureFlagSnapshot,
        ): FeatureFlagAuditLog = FeatureFlagAuditLog(
            flagKey = after.key,
            changeType = changeType,
            actorUserId = actorUserId,
            beforeSnapshot = before,
            afterSnapshot = after,
            occurredAt = ZonedDateTime.now(),
        )
    }
}
