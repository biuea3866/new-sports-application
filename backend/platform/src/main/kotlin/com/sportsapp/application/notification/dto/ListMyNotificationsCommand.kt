package com.sportsapp.application.notification.dto

import com.sportsapp.domain.notification.vo.NotificationChannel

/**
 * [channel] 이 null 이면 전 채널을 조회한다 — 알림함은 IN_APP 을, MCP 발송 진단 도구는 null 을 넘긴다.
 */
data class ListMyNotificationsCommand(
    val userId: Long,
    val channel: NotificationChannel?,
    val onlyUnread: Boolean,
    val page: Int,
    val size: Int,
)
