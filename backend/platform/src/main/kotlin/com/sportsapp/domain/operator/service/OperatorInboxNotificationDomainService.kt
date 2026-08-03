package com.sportsapp.domain.operator.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.repository.OperatorInboxNotificationRepository
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class OperatorInboxNotificationDomainService(
    private val repository: OperatorInboxNotificationRepository,
) {
    fun create(
        recipientUserId: Long,
        type: OperatorInboxNotificationType,
        title: String,
        body: String,
        link: String?,
    ): OperatorInboxNotification =
        repository.save(OperatorInboxNotification.create(recipientUserId, type, title, body, link))

    /**
     * 도메인 이벤트로부터 운영 알림을 적재하되, 같은 이벤트를 이미 받았으면 건너뛴다.
     *
     * Kafka는 at-least-once라 중복 수신이 정상 시나리오다. 멱등 처리가 없으면 파트너 인박스에
     * 같은 알림이 쌓이고 안읽음 배지도 함께 부풀어 오른다. 이미 적재된 경우 null을 반환한다.
     */
    fun createOrSkip(
        eventId: String,
        recipientUserId: Long,
        type: OperatorInboxNotificationType,
        title: String,
        body: String,
        link: String?,
    ): OperatorInboxNotification? {
        if (repository.existsByRecipientUserIdAndEventId(recipientUserId, eventId)) return null
        return repository.save(
            OperatorInboxNotification.create(recipientUserId, type, title, body, link, eventId)
        )
    }

    fun listByRecipient(
        recipientUserId: Long,
        type: OperatorInboxNotificationType?,
        status: OperatorInboxNotificationStatus?,
        pageable: Pageable,
    ): Page<OperatorInboxNotification> =
        repository.findByRecipientPaged(recipientUserId, type, status, pageable)

    fun updateStatus(
        notificationId: Long,
        recipientUserId: Long,
        targetStatus: OperatorInboxNotificationStatus,
    ): OperatorInboxNotification {
        val notification = repository.findByIdAndRecipientUserId(notificationId, recipientUserId)
            ?: throw ResourceNotFoundException("OperatorInboxNotification", notificationId)
        when (targetStatus) {
            OperatorInboxNotificationStatus.READ -> notification.markRead()
            OperatorInboxNotificationStatus.ARCHIVED -> notification.archive()
            OperatorInboxNotificationStatus.UNREAD -> throw IllegalArgumentException("Cannot transition to UNREAD")
        }
        return repository.save(notification)
    }

    fun countUnread(recipientUserId: Long): Long =
        repository.countUnreadByRecipientUserId(recipientUserId)
}
