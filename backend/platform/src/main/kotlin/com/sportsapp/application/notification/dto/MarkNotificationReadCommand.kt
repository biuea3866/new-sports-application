package com.sportsapp.application.notification.dto
data class MarkNotificationReadCommand(
    val notificationId: Long,
    val userId: Long,
)
