package com.sportsapp.application.notification.dto

import com.sportsapp.domain.notification.dto.NotificationView
import org.springframework.data.domain.Page

/**
 * 알림함 목록(GET /notifications/me) 결과.
 *
 * 저장 원형이 아니라 렌더가 끝난 [NotificationView] 를 담는다 — 목록 응답에 제목·본문이 없어
 * 앱 알림함이 빈 줄로 렌더되던 회귀(유즈케이스 캡쳐 36-알림함)를 막기 위해서다.
 */
data class NotificationPageResult(
    val content: List<NotificationView>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<NotificationView>) = NotificationPageResult(
            content = page.content,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
