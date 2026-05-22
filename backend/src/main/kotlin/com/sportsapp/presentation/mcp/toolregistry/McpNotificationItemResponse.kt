package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.notification.NotificationResponse
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationStatus
import java.time.ZonedDateTime

/**
 * MCP tool 전용 알림 응답 DTO.
 * NotificationResponse 에서 내부 PK(userId) 를 제외한 MCP 노출용 DTO.
 */
data class McpNotificationItemResponse(
    val id: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val status: NotificationStatus,
    val sentAt: ZonedDateTime?,
    val readAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(notification: NotificationResponse) = McpNotificationItemResponse(
            id = notification.id,
            channel = notification.channel,
            templateId = notification.templateId,
            status = notification.status,
            sentAt = notification.sentAt,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
    }
}
