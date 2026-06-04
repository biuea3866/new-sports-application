package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationChannel

data class SendNotificationCommand(
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val payload: Map<String, Any>,
)
