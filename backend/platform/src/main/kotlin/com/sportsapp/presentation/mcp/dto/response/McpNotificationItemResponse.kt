package com.sportsapp.presentation.mcp.dto.response

import com.sportsapp.domain.notification.dto.NotificationView
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.entity.NotificationStatus
import java.time.ZonedDateTime

/**
 * MCP tool 전용 알림 응답 DTO.
 * NotificationView 에서 내부 PK(userId) 를 제외한 MCP 노출용 DTO.
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
        fun of(view: NotificationView) = McpNotificationItemResponse(
            id = view.id,
            channel = view.channel,
            templateId = view.templateId,
            status = view.status,
            sentAt = view.sentAt,
            readAt = view.readAt,
            createdAt = view.createdAt,
        )
    }
}
