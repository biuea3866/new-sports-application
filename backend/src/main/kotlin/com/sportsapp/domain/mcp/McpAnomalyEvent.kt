package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "mcp_anomaly_events")
class McpAnomalyEvent(
    @Column(name = "source_event_id", nullable = false, unique = true, length = 36)
    val sourceEventId: String,

    @Column(name = "token_id", nullable = false)
    val tokenId: Long,

    @Column(name = "owner_user_id", nullable = false)
    val ownerUserId: Long,

    @Column(name = "detected_at", nullable = false)
    val detectedAt: ZonedDateTime,

    @Column(name = "current_hour_count", nullable = false)
    val currentHourCount: Long,

    @Column(name = "baseline_average", nullable = false)
    val baselineAverage: Double,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: McpAnomalyEventStatus = McpAnomalyEventStatus.OPEN
        private set

    @Column(name = "resolved_at")
    var resolvedAt: ZonedDateTime? = null
        private set

    @Column(name = "resolved_by")
    var resolvedBy: Long? = null
        private set

    @Column(name = "note", length = 50)
    var note: String? = null
        private set

    @Column(name = "false_positive", nullable = false)
    var falsePositive: Boolean = false
        private set

    fun markFalsePositive(userId: Long, noteText: String?) {
        check(status.canTransitTo(McpAnomalyEventStatus.FALSE_POSITIVE)) {
            "Cannot mark false positive: current status=$status"
        }
        status = McpAnomalyEventStatus.FALSE_POSITIVE
        falsePositive = true
        resolvedAt = ZonedDateTime.now()
        resolvedBy = userId
        note = noteText
    }

    fun resolve(userId: Long, noteText: String?) {
        check(status.canTransitTo(McpAnomalyEventStatus.RESOLVED)) {
            "Cannot resolve: current status=$status"
        }
        status = McpAnomalyEventStatus.RESOLVED
        resolvedAt = ZonedDateTime.now()
        resolvedBy = userId
        note = noteText
    }

    fun requireOwnedBy(userId: Long) {
        if (ownerUserId != userId) throw McpAnomalyEventNotOwnedException(id)
    }

    companion object {
        fun of(event: McpAnomalyDetectedEvent): McpAnomalyEvent = McpAnomalyEvent(
            sourceEventId = event.eventId,
            tokenId = event.tokenId,
            ownerUserId = event.userId,
            detectedAt = event.occurredAt,
            currentHourCount = event.currentHourCount,
            baselineAverage = event.baselineAverage,
        )
    }
}
