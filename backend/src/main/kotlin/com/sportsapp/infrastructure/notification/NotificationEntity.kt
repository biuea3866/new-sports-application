package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationStatus
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

@Entity
@Table(name = "notifications")
class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

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

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime,
) {
    fun toDomain(): Notification = Notification(
        id = id,
        userId = userId,
        channel = channel,
        templateId = templateId,
        payload = payload,
        status = status,
        sentAt = sentAt,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(notification: Notification): NotificationEntity = NotificationEntity(
            id = notification.id,
            userId = notification.userId,
            channel = notification.channel,
            templateId = notification.templateId,
            payload = notification.payload,
            status = notification.status,
            sentAt = notification.sentAt,
            createdAt = notification.createdAt,
        )
    }
}
