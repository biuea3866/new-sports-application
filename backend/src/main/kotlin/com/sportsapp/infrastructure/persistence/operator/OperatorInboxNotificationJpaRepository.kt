package com.sportsapp.infrastructure.persistence.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotification
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OperatorInboxNotificationJpaRepository :
    JpaRepository<OperatorInboxNotification, Long>,
    OperatorInboxNotificationQueryDslRepository {

    fun findByIdAndDeletedAtIsNull(id: Long): OperatorInboxNotification?
    fun findByIdAndRecipientUserIdAndDeletedAtIsNull(id: Long, recipientUserId: Long): OperatorInboxNotification?
    fun countByRecipientUserIdAndStatusAndDeletedAtIsNull(
        recipientUserId: Long,
        status: OperatorInboxNotificationStatus,
    ): Long
}
