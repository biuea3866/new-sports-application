package com.sportsapp.domain.notification

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val SUPPORTED_ENQUEUE_CHANNELS = setOf(NotificationChannel.IN_APP)

@Service
class NotificationDomainService(
    private val notificationRepository: NotificationRepository,
    private val notificationCustomRepository: NotificationCustomRepository,
    private val channelGateways: List<NotificationChannelGateway>,
    private val templateRenderer: TemplateRenderer,
) {
    @Transactional
    fun send(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: NotificationPayload?,
    ): Notification = dispatchNotification(Notification.queue(userId, channel, templateId, payload))

    @Transactional(noRollbackFor = [DataIntegrityViolationException::class])
    fun enqueueOrSkip(
        eventId: String,
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: NotificationPayload?,
    ): Notification? {
        if (channel !in SUPPORTED_ENQUEUE_CHANNELS) throw UnsupportedChannelException(channel)
        if (notificationRepository.findByEventId(eventId) != null) return null
        return try {
            dispatchNotification(
                Notification.queue(
                    userId = userId,
                    channel = channel,
                    templateId = templateId,
                    payload = payload,
                    eventId = eventId,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            notificationRepository.findByEventId(eventId)
        }
    }

    private fun dispatchNotification(queued: Notification): Notification {
        val notification = notificationRepository.save(queued)
        val gateway = channelGateways.find { it.supportedChannel == notification.channel }
            ?: return markFailedAndSave(notification)

        val result = gateway.send(notification)
        return if (result.success) {
            notification.markSent()
            notificationRepository.save(notification)
        } else {
            markFailedAndSave(notification)
        }
    }

    fun listMyNotifications(userId: Long, onlyUnread: Boolean, page: Int, size: Int): Page<Notification> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return notificationCustomRepository.findByUserIdPaged(userId, onlyUnread, pageable)
    }

    @Transactional
    fun markRead(notificationId: Long, userId: Long): Notification {
        val notification = notificationRepository.findById(notificationId)
            ?: throw NotificationNotFoundException(notificationId)
        notification.requireOwnedBy(userId)
        notification.markRead()
        return notificationRepository.save(notification)
    }

    @Transactional
    fun sendWithTemplate(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: Map<String, Any>,
    ): Notification {
        val rendered = templateRenderer.render(templateId, payload)
        val enrichedPayload = NotificationPayload(
            payload + mapOf("_title" to rendered.title, "_body" to rendered.body)
        )
        return send(userId, channel, templateId, enrichedPayload)
    }

    fun countUnread(userId: Long): Long =
        notificationRepository.countUnreadByUserId(userId)

    private fun markFailedAndSave(notification: Notification): Notification {
        notification.markFailed()
        return notificationRepository.save(notification)
    }
}
