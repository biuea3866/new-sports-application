package com.sportsapp.domain.notification

import com.sportsapp.application.notification.NotificationResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val SUPPORTED_ENQUEUE_CHANNELS = setOf(
    NotificationChannel.IN_APP,
    NotificationChannel.PUSH,
    NotificationChannel.EMAIL,
    NotificationChannel.SMS,
)

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
    ): NotificationResponse = NotificationResponse.of(dispatchNotification(Notification.queue(userId, channel, templateId, payload)))

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
                    payload = enrichPayload(templateId, payload),
                    eventId = eventId,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            notificationRepository.findByEventId(eventId)
        }
    }

    // 이벤트 기반 알림도 제목/본문을 렌더해 PUSH/EMAIL/SMS 채널이 내용을 갖도록 한다.
    // 알 수 없는 템플릿이면 원본 payload 를 그대로 둔다(IN_APP 는 영향 없음).
    private fun enrichPayload(templateId: String, payload: NotificationPayload?): NotificationPayload {
        val base = payload?.data ?: emptyMap()
        if (base.containsKey("_title")) return NotificationPayload(base)
        return try {
            val rendered = templateRenderer.render(templateId, base)
            NotificationPayload(base + mapOf("_title" to rendered.title, "_body" to rendered.body))
        } catch (exception: UnknownTemplateException) {
            NotificationPayload(base)
        }
    }

    private fun dispatchNotification(queued: Notification): Notification {
        val notification = notificationRepository.save(queued)
        val gateway = channelGateways.find { it.supportedChannel == notification.channel }
            ?: return markFailedAndSave(notification)

        val sendResult = gateway.send(notification)
        return if (sendResult.success) {
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
    fun markRead(notificationId: Long, userId: Long): NotificationResponse {
        val notification = notificationRepository.findById(notificationId)
            ?: throw NotificationNotFoundException(notificationId)
        notification.requireOwnedBy(userId)
        notification.markRead()
        return NotificationResponse.of(notificationRepository.save(notification))
    }

    @Transactional
    fun sendWithTemplate(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: Map<String, Any>,
    ): NotificationResponse {
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
