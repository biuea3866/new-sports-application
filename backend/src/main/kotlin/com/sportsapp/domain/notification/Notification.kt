package com.sportsapp.domain.notification

import com.sportsapp.domain.common.JpaAuditingBase
import io.hypersistence.utils.hibernate.type.json.JsonStringType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Type
import java.time.ZonedDateTime

@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    val channel: NotificationChannel,

    @Column(name = "template_id", nullable = false, length = 100)
    val templateId: String,

    @Type(JsonStringType::class)
    @Column(name = "payload", columnDefinition = "TEXT")
    val payload: NotificationPayload,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: NotificationStatus,

    @Column(name = "sent_at")
    var sentAt: ZonedDateTime?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    fun markSent() {
        if (!status.canTransitToSent()) throw InvalidNotificationStateException(status)
        status = NotificationStatus.SENT
        sentAt = ZonedDateTime.now()
    }

    fun markFailed() {
        status = NotificationStatus.FAILED
    }

    companion object {
        fun queue(
            userId: Long,
            channel: NotificationChannel,
            templateId: String,
            payload: NotificationPayload?,
        ): Notification = Notification(
            userId = userId,
            channel = channel,
            templateId = templateId,
            payload = payload ?: NotificationPayload(emptyMap()),
            status = NotificationStatus.QUEUED,
            sentAt = null,
        )
    }
}
