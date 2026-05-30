package com.sportsapp.domain.operator.inbox

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
}
