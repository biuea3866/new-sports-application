package com.sportsapp.domain.notification

import org.springframework.stereotype.Service

@Service
class NotificationDomainService(
    private val notificationRepository: NotificationRepository,
    private val channelGateways: List<NotificationChannelGateway>,
) {
    fun send(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: NotificationPayload?,
    ): Notification {
        val notification = notificationRepository.save(
            Notification.queue(userId, channel, templateId, payload)
        )
        val gateway = channelGateways.find { it.supportedChannel == channel }
            ?: return saveAsFailed(notification, UnsupportedChannelException(channel))

        val result = gateway.send(notification)
        return if (result.success) {
            notification.markSent()
            notificationRepository.save(notification)
        } else {
            saveAsFailed(notification, RuntimeException(result.errorMessage))
        }
    }

    private fun saveAsFailed(notification: Notification, cause: Exception): Notification {
        notification.markFailed()
        notificationRepository.save(notification)
        throw cause
    }
}
