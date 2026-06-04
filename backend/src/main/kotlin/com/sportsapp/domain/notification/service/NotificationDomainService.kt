package com.sportsapp.domain.notification

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.notification.exception.UnsupportedChannelException
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
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun send(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: NotificationPayload?,
    ): NotificationResult {
        val notification = persistQueued(Notification.queue(userId, channel, templateId, payload))
        domainEventPublisher.publish(NotificationDispatchRequestedEvent(notificationId = notification.id))
        return NotificationResult.of(notification)
    }

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
            val queued = Notification.queue(
                userId = userId,
                channel = channel,
                templateId = templateId,
                payload = enrichPayload(templateId, payload),
                eventId = eventId,
            )
            val notification = persistQueued(queued)
            domainEventPublisher.publish(NotificationDispatchRequestedEvent(notificationId = notification.id))
            notification
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

    private fun persistQueued(queued: Notification): Notification =
        notificationRepository.save(queued)

    /**
     * AFTER_COMMIT 시점에 gateway 발송을 수행한다.
     * @Transactional 을 달지 않는다 — 발송 결과만 DB 에 반영하므로 별도 트랜잭션으로 처리한다.
     */
    @Transactional
    fun dispatchById(notificationId: Long) {
        val notification = notificationRepository.findById(notificationId) ?: return
        val gateway = channelGateways.find { it.supportedChannel == notification.channel }
        if (gateway == null) {
            notification.markFailed()
            notificationRepository.save(notification)
            return
        }
        val sendResult = gateway.send(notification)
        if (sendResult.success) {
            notification.markSent()
        } else {
            notification.markFailed()
        }
        notificationRepository.save(notification)
    }

    fun listMyNotifications(userId: Long, onlyUnread: Boolean, page: Int, size: Int): Page<Notification> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return notificationCustomRepository.findByUserIdPaged(userId, onlyUnread, pageable)
    }

    @Transactional
    fun markRead(notificationId: Long, userId: Long): NotificationResult {
        val notification = notificationRepository.findById(notificationId)
            ?: throw NotificationNotFoundException(notificationId)
        notification.requireOwnedBy(userId)
        notification.markRead()
        return NotificationResult.of(notificationRepository.save(notification))
    }

    @Transactional
    fun sendWithTemplate(
        userId: Long,
        channel: NotificationChannel,
        templateId: String,
        payload: Map<String, Any>,
    ): NotificationResult {
        val rendered = templateRenderer.render(templateId, payload)
        val enrichedPayload = NotificationPayload(
            payload + mapOf("_title" to rendered.title, "_body" to rendered.body)
        )
        return send(userId, channel, templateId, enrichedPayload)
    }

    fun countUnread(userId: Long): Long =
        notificationRepository.countUnreadByUserId(userId)
}
