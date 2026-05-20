package com.sportsapp.domain.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationDomainService(
    private val notificationRepository: NotificationRepository,
    private val customNotificationRepository: CustomNotificationRepository,
    private val channelGateways: List<NotificationChannelGateway>,
    private val templateRenderer: TemplateRenderer,
) {
    @Transactional(noRollbackFor = [Exception::class])
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

    fun listMyNotifications(userId: Long, onlyUnread: Boolean, page: Int, size: Int): Page<Notification> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return customNotificationRepository.findByUserIdPaged(userId, onlyUnread, pageable)
    }

    @Transactional
    fun markRead(notificationId: Long, userId: Long): Notification {
        val notification = notificationRepository.findById(notificationId)
            ?: throw NotificationNotFoundException(notificationId)
        notification.requireOwnedBy(userId)
        notification.markRead()
        return notificationRepository.save(notification)
    }

    @Transactional(noRollbackFor = [Exception::class])
    fun sendWithTemplate(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: Map<String, Any>,
    ): Notification {
        templateRenderer.render(templateId, payload)
        return send(userId, channel, templateId, NotificationPayload(payload))
    }

    fun countUnread(userId: Long): Long =
        notificationRepository.countUnreadByUserId(userId)

    private fun saveAsFailed(notification: Notification, cause: Exception): Notification {
        notification.markFailed()
        notificationRepository.save(notification)
        throw cause
    }
}
