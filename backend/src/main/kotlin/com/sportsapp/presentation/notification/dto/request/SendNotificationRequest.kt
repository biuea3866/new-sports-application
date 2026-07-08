package com.sportsapp.presentation.notification.dto.request
import com.sportsapp.application.notification.dto.SendNotificationCommand
import com.sportsapp.domain.notification.vo.NotificationChannel
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
