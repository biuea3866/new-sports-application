package com.sportsapp.domain.notification

import java.time.ZonedDateTime

class Notification(
    val id: Long,
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val payload: NotificationPayload,
    var status: NotificationStatus,
    var sentAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
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
            id = 0L,
            userId = userId,
            channel = channel,
            templateId = templateId,
            payload = payload ?: NotificationPayload(emptyMap()),
            status = NotificationStatus.QUEUED,
            sentAt = null,
            createdAt = ZonedDateTime.now(),
        )
    }
}
