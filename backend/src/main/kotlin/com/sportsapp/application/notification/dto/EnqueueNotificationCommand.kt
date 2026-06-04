package com.sportsapp.application.notification.dto
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
data class EnqueueNotificationCommand(
    val channel: NotificationChannel,
    val templateId: String,
    val payload: NotificationPayload,
    val recipientUserId: Long,
    val eventId: String,
)
