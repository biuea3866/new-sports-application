package com.sportsapp.domain.notification.entity
import com.sportsapp.domain.common.JpaAuditingBase
import io.hypersistence.utils.hibernate.type.json.JsonStringType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Type
import java.time.ZoneOffset
import java.time.ZonedDateTime
import com.sportsapp.domain.notification.exception.InvalidNotificationStateException
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.exception.NotificationNotOwnedException
import com.sportsapp.domain.notification.vo.NotificationPayload

@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    val channel: NotificationChannel,

    @Column(name = "template_id", nullable = false, length = 100)
    val templateId: String,

    @Type(JsonStringType::class)
    @Column(name = "payload", columnDefinition = "TEXT")
    val payload: NotificationPayload,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: NotificationStatus,

    @Column(name = "sent_at")
    var sentAt: ZonedDateTime?,

    @Column(name = "read_at")
    var readAt: ZonedDateTime?,

    @Column(name = "event_id", length = 128)
    val eventId: String?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    fun markSent() {
        if (!status.canTransitToSent()) throw InvalidNotificationStateException(status)
        status = NotificationStatus.SENT
        sentAt = ZonedDateTime.now(ZoneOffset.UTC)
    }

    fun markFailed() {
        if (!status.canTransitToFailed()) throw InvalidNotificationStateException(status)
        status = NotificationStatus.FAILED
    }

    fun markRead() {
        if (readAt != null) return
        readAt = ZonedDateTime.now(ZoneOffset.UTC)
    }

    fun requireOwnedBy(requestUserId: Long) {
        if (userId != requestUserId) throw NotificationNotOwnedException(id, requestUserId)
    }

    /** 읽음 여부 — 별도 플래그 컬럼 없이 readAt 존재 여부가 곧 상태다. */
    val isRead: Boolean
        get() = readAt != null

    /**
     * 적재 시점에 렌더돼 payload 에 보관된 제목·본문(`_title`/`_body`).
     * 발송 채널이 쓰던 값을 알림함이 재사용해 조회마다 템플릿을 다시 렌더하지 않게 한다.
     * 비어 있으면 null 을 돌려 호출부가 렌더로 폴백하게 한다.
     */
    fun renderedTitle(): String? = payloadText("_title")

    fun renderedBody(): String? = payloadText("_body")

    fun payloadData(): Map<String, Any> = payload.data

    // 변수 치환이 빈 값으로 이뤄져 앞뒤 공백이 남은 채 저장된 기존 행이 있어(캡쳐 36-알림함
    // booking-confirmed " 예약이 확정되었습니다.") 노출 시점에 정리한다.
    private fun payloadText(key: String): String? =
        payload.data[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        fun queue(
            userId: Long,
            channel: NotificationChannel,
            templateId: String,
            payload: NotificationPayload?,
            eventId: String? = null,
        ): Notification = Notification(
            userId = userId,
            channel = channel,
            templateId = templateId,
            payload = payload ?: NotificationPayload(emptyMap()),
            status = NotificationStatus.QUEUED,
            sentAt = null,
            readAt = null,
            eventId = eventId,
        )
    }
}
