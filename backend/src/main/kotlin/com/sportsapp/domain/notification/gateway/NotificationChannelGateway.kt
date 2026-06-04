package com.sportsapp.domain.notification

interface NotificationChannelGateway {
    val supportedChannel: NotificationChannel
    fun send(notification: Notification): SendResult
}

data class SendResult(
    val success: Boolean,
    val errorMessage: String?,
)
