package com.sportsapp.domain.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
interface NotificationChannelGateway {
    val supportedChannel: NotificationChannel
    fun send(notification: Notification): SendResult
}

data class SendResult(
    val success: Boolean,
    val errorMessage: String?,
)
