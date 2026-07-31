package com.sportsapp.presentation.mcp.dto.response

import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.entity.NotificationStatus
import java.time.ZonedDateTime

/**
 * MCP tool 전용 알림 응답 DTO.
 * NotificationResult 에서 내부 PK(userId) 를 제외한 MCP 노출용 DTO.
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
        fun of(result: NotificationResult) = McpNotificationItemResponse(
            id = result.id,
            channel = result.channel,
            templateId = result.templateId,
            status = result.status,
            sentAt = result.sentAt,
            readAt = result.readAt,
            createdAt = result.createdAt,
        )
    }
}
