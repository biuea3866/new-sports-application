package com.sportsapp.domain.notification.dto

import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.domain.notification.vo.NotificationCategory
import com.sportsapp.domain.notification.vo.NotificationChannel
import java.time.ZonedDateTime

/**
 * 알림 1건의 읽기 모델.
 *
 * 저장 원형([com.sportsapp.domain.notification.entity.Notification])이 templateId + payload 로
 * 들고 있던 값을 렌더해 제목·본문·분류·읽음 여부까지 확정한다. 발송 메타데이터(channel·status·
 * sentAt)도 함께 담아 소비처가 필요한 만큼만 좁혀 쓴다 — 앱 알림함은
 * [com.sportsapp.presentation.notification.dto.response.MyNotificationResponse] 로,
 * MCP tool 은 자기 DTO 로 각각 좁힌다.
 */
data class NotificationView(
    val id: Long,
    val userId: Long,
    val title: String,
    val content: String,
    val category: NotificationCategory,
    val channel: NotificationChannel,
    val templateId: String,
    val status: NotificationStatus,
    val isRead: Boolean,
    val sentAt: ZonedDateTime?,
    val readAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
)
