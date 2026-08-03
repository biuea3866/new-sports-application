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

    /** 이벤트 멱등 판정 — 멱등 범위는 (수신자, 이벤트)다. 이벤트 단독이면 한 수신자가 받는 순간 나머지가 못 받는다. */
    fun existsByRecipientUserIdAndEventId(recipientUserId: Long, eventId: String): Boolean
}
