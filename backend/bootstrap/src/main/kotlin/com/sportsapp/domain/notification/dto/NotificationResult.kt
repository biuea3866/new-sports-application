package com.sportsapp.domain.notification.dto
import java.time.ZonedDateTime
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.entity.NotificationStatus

data class NotificationResult(
    val id: Long,
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val status: NotificationStatus,
    val sentAt: ZonedDateTime?,
    val readAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(notification: Notification) = NotificationResult(
            id = notification.id,
            userId = notification.userId,
            channel = notification.channel,
            templateId = notification.templateId,
            status = notification.status,
            sentAt = notification.sentAt,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
    }
}
