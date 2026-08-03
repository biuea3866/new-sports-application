package com.sportsapp.domain.notification.service
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.notification.exception.UnsupportedChannelException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.gateway.NotificationChannelGateway
import com.sportsapp.domain.notification.repository.NotificationCustomRepository
import com.sportsapp.domain.notification.NotificationDispatchRequestedEvent
import com.sportsapp.domain.notification.exception.NotificationNotFoundException
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.notification.repository.NotificationRepository
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.dto.NotificationView
import com.sportsapp.domain.notification.gateway.TemplateRenderer
import com.sportsapp.domain.notification.exception.UnknownTemplateException
import com.sportsapp.domain.notification.vo.NotificationCategory
import com.sportsapp.domain.notification.vo.RenderedNotification

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

    /**
     * 사용자 알림 목록. 채널은 **호출자가 결정한다** — 알림함은 IN_APP(같은 사건이 IN_APP·PUSH
     * 두 행으로 적재돼 중복 노출되므로), MCP 발송 진단 도구는 null(전 채널).
     * 도메인이 채널을 하드코딩하면 재사용 지점이 전부 끌려간다.
     */
    fun listMyNotifications(
        userId: Long,
        channel: NotificationChannel?,
        onlyUnread: Boolean,
        page: Int,
        size: Int,
    ): Page<Notification> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return notificationCustomRepository.findByUserIdPaged(
            userId = userId,
            channel = channel,
            onlyUnread = onlyUnread,
            pageable = pageable,
        )
    }

    /**
     * 알림함 목록 — 저장 원형을 사용자에게 보여줄 제목·본문·분류·읽음 여부까지 확정해 돌려준다.
     * 목록 화면이 templateId/status 같은 내부 값을 해석하지 않게 하려는 것이 목적이다.
     */
    fun listMyNotificationViews(
        userId: Long,
        channel: NotificationChannel?,
        onlyUnread: Boolean,
        page: Int,
        size: Int,
    ): Page<NotificationView> =
        listMyNotifications(userId, channel, onlyUnread, page, size).map { toView(it) }

    private fun toView(notification: Notification): NotificationView {
        val rendered = resolveRendered(notification)
        return NotificationView(
            id = notification.id,
            userId = notification.userId,
            title = rendered.title,
            content = rendered.body,
            category = NotificationCategory.from(notification.templateId),
            channel = notification.channel,
            templateId = notification.templateId,
            status = notification.status,
            isRead = notification.isRead,
            sentAt = notification.sentAt,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
    }

    /**
     * 적재 시 저장된 렌더 결과를 우선 쓰고, 없으면 템플릿을 렌더한다.
     * 설정에 없는 템플릿 1건이 목록 전체를 실패시키지 않도록 기본 문구로 방어한다.
     */
    private fun resolveRendered(notification: Notification): RenderedNotification {
        val storedTitle = notification.renderedTitle()
        if (storedTitle != null) {
            return RenderedNotification(title = storedTitle, body = notification.renderedBody() ?: "")
        }
        return try {
            templateRenderer.render(notification.templateId, notification.payloadData())
        } catch (exception: UnknownTemplateException) {
            RenderedNotification(title = FALLBACK_TITLE, body = "")
        }
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

    /**
     * 미읽음 배지. 목록과 **같은 채널 기준**으로 세야 배지와 목록 건수가 어긋나지 않는다.
     */
    fun countUnread(userId: Long, channel: NotificationChannel?): Long =
        notificationRepository.countUnreadByUserId(userId, channel)

    companion object {
        /** 설정에 없는 템플릿으로 적재된 알림의 기본 제목. */
        const val FALLBACK_TITLE = "알림"
    }
}
