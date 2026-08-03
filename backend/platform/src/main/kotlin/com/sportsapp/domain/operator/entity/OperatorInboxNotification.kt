package com.sportsapp.domain.operator.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Entity
@Table(name = "operator_inbox_notifications")
class OperatorInboxNotification(
    @Column(name = "recipient_user_id", nullable = false)
    val recipientUserId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    val type: OperatorInboxNotificationType,

    @Column(name = "title", nullable = false, length = 200)
    val title: String,

    @Column(name = "body", nullable = false, length = 1000)
    val body: String,

    @Column(name = "link", length = 500)
    val link: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OperatorInboxNotificationStatus,

    @Column(name = "read_at")
    var readAt: ZonedDateTime?,

    /**
     * 이벤트 멱등 키 — 같은 도메인 이벤트를 두 번 수신해도 중복 적재되지 않게 한다.
     * 이벤트에서 비롯되지 않은 적재(운영자 수동 생성 등)는 null이다.
     */
    @Column(name = "event_id", length = 100)
    val eventId: String?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun markRead() {
        if (status == OperatorInboxNotificationStatus.READ) return
        check(status.canMarkRead()) { "Cannot mark read: status=$status" }
        status = OperatorInboxNotificationStatus.READ
        readAt = ZonedDateTime.now(ZoneOffset.UTC)
    }

    fun archive() {
        check(status.canArchive()) { "Cannot archive notification with status=$status" }
        status = OperatorInboxNotificationStatus.ARCHIVED
    }

    companion object {
        fun create(
            recipientUserId: Long,
            type: OperatorInboxNotificationType,
            title: String,
            body: String,
            link: String?,
            eventId: String? = null,
        ): OperatorInboxNotification = OperatorInboxNotification(
            recipientUserId = recipientUserId,
            type = type,
            title = title,
            body = body,
            link = link,
            status = OperatorInboxNotificationStatus.UNREAD,
            readAt = null,
            eventId = eventId,
        )
    }
}
