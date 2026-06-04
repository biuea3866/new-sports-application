package com.sportsapp.presentation.notification

import com.sportsapp.application.notification.SendNotificationCommand
import com.sportsapp.domain.notification.NotificationChannel

data class SendNotificationRequest(
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val payload: Map<String, Any> = emptyMap(),
) {
    fun toCommand() = SendNotificationCommand(
        userId = userId,
        channel = channel,
        templateId = templateId,
        payload = payload,
    )
}
