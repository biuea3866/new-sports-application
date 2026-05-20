package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload

data class EnqueueNotificationCommand(
    val channel: NotificationChannel,
    val templateId: String,
    val payload: NotificationPayload,
    val recipientUserId: Long,
    val eventId: String,
)
