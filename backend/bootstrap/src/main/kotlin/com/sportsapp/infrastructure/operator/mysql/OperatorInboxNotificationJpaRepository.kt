package com.sportsapp.infrastructure.operator.mysql

import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
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
