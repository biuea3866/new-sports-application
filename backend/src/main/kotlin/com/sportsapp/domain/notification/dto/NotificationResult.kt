package com.sportsapp.domain.notification

import java.time.ZonedDateTime

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
