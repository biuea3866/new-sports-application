package com.sportsapp.domain.operator.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.repository.OperatorInboxNotificationRepository
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
     *
     * 선행 조회만으로는 부족하다 — 리밸런싱·재처리로 같은 이벤트가 동시에 처리되면 두 경로가
     * 모두 "없음"을 보고 insert해 UNIQUE 위반이 리스너까지 올라간다. DB 제약을 최종 방어선으로
     * 두고 위반을 흡수한다(notification의 `enqueueOrSkip`과 동일 패턴).
     * `noRollbackFor`가 없으면 흡수해도 트랜잭션이 롤백 표시돼 호출부가 실패한다.
     */
    @Transactional(noRollbackFor = [DataIntegrityViolationException::class])
    fun createOrSkip(
        eventId: String,
        recipientUserId: Long,
        type: OperatorInboxNotificationType,
        title: String,
        body: String,
        link: String?,
    ): OperatorInboxNotification? {
        if (repository.existsByRecipientUserIdAndEventId(recipientUserId, eventId)) return null
        return try {
            repository.save(OperatorInboxNotification.create(recipientUserId, type, title, body, link, eventId))
        } catch (duplicateEvent: DataIntegrityViolationException) {
            // 동시 처리로 먼저 들어간 행이 있다 — 중복 수신은 정상 시나리오이므로 건너뛴다.
            null
        }
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
