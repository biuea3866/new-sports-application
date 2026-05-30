package com.sportsapp.application.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotification
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

data class OperatorInboxNotificationResponse(
    val id: Long,
    val recipientUserId: Long,
    val type: OperatorInboxNotificationType,
    val title: String,
    val body: String,
    val link: String?,
    val status: OperatorInboxNotificationStatus,
    val readAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(notification: OperatorInboxNotification) = OperatorInboxNotificationResponse(
            id = notification.id,
            recipientUserId = notification.recipientUserId,
            type = notification.type,
            title = notification.title,
            body = notification.body,
            link = notification.link,
            status = notification.status,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
    }
}

data class OperatorInboxNotificationPageResponse(
    val content: List<OperatorInboxNotificationResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<OperatorInboxNotification>) = OperatorInboxNotificationPageResponse(
            content = page.content.map { OperatorInboxNotificationResponse.of(it) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}

data class OperatorInboxUnreadCountResponse(val unreadCount: Long)
