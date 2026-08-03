package com.sportsapp.domain.operator.repository

import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OperatorInboxNotificationRepository {
    fun save(notification: OperatorInboxNotification): OperatorInboxNotification
    fun findById(id: Long): OperatorInboxNotification?
    fun findByIdAndRecipientUserId(id: Long, recipientUserId: Long): OperatorInboxNotification?
    fun findByRecipientPaged(
        recipientUserId: Long,
        type: OperatorInboxNotificationType?,
        status: OperatorInboxNotificationStatus?,
        pageable: Pageable,
    ): Page<OperatorInboxNotification>
    fun countUnreadByRecipientUserId(recipientUserId: Long): Long

    /**
     * 이벤트 멱등 판정 — 멱등 범위는 (수신자, 이벤트)다. 이벤트 단독이면 한 수신자가 받는
     * 순간 나머지가 못 받는다.
     *
     * **소프트 삭제 행도 포함해 판정한다** — UNIQUE 인덱스(uk_operator_inbox_recipient_event)가
     * 삭제 행을 포함하므로, 여기서 제외하면 기준이 어긋나 한 번 삭제된 이벤트가 영영 적재
     * 불가한 poison 상태가 된다.
     */
    fun existsByRecipientUserIdAndEventId(recipientUserId: Long, eventId: String): Boolean
}
