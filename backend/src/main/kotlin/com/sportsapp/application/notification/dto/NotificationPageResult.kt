package com.sportsapp.application.notification.dto

import com.sportsapp.domain.notification.NotificationResult
import com.sportsapp.domain.notification.Notification
import org.springframework.data.domain.Page

data class NotificationPageResult(
    val content: List<NotificationResult>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<Notification>) = NotificationPageResult(
            content = page.content.map { NotificationResult.of(it) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
