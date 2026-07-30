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
