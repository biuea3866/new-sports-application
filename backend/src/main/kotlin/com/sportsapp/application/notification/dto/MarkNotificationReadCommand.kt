package com.sportsapp.application.notification

data class MarkNotificationReadCommand(
    val notificationId: Long,
    val userId: Long,
)
