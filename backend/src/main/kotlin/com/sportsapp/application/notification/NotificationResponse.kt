package com.sportsapp.application.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationStatus
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

data class NotificationResponse(
    val id: Long,
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val status: NotificationStatus,
    val sentAt: ZonedDateTime?,
    val readAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(notification: Notification) = NotificationResponse(
            id = notification.id,
            userId = notification.userId,
            channel = notification.channel,
            templateId = notification.templateId,
            status = notification.status,
            sentAt = notification.sentAt,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
    }
}

data class NotificationPageResponse(
    val content: List<NotificationResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<Notification>) = NotificationPageResponse(
            content = page.content.map { NotificationResponse.of(it) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}

data class UnreadCountResponse(val unreadCount: Long)
