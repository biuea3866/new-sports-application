package com.sportsapp.infrastructure.persistence.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotification
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationRepository
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class OperatorInboxNotificationRepositoryImpl(
    private val jpaRepository: OperatorInboxNotificationJpaRepository,
) : OperatorInboxNotificationRepository {

    override fun save(notification: OperatorInboxNotification): OperatorInboxNotification =
        jpaRepository.save(notification)

    override fun findById(id: Long): OperatorInboxNotification? =
        jpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByIdAndRecipientUserId(id: Long, recipientUserId: Long): OperatorInboxNotification? =
        jpaRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(id, recipientUserId)

    override fun findByRecipientPaged(
        recipientUserId: Long,
        type: OperatorInboxNotificationType?,
        status: OperatorInboxNotificationStatus?,
        pageable: Pageable,
    ): Page<OperatorInboxNotification> =
        jpaRepository.findByRecipientPaged(recipientUserId, type, status, pageable)

    override fun countUnreadByRecipientUserId(recipientUserId: Long): Long =
        jpaRepository.countByRecipientUserIdAndStatusAndDeletedAtIsNull(
            recipientUserId,
            OperatorInboxNotificationStatus.UNREAD,
        )
}
