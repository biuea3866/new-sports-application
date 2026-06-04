package com.sportsapp.application.notification.dto
import com.sportsapp.domain.notification.vo.NotificationChannel
data class SendNotificationCommand(
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val payload: Map<String, Any>,
)
