package com.sportsapp.infrastructure.persistence.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotification
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OperatorInboxNotificationQueryDslRepository {
    fun findByRecipientPaged(
        recipientUserId: Long,
        type: OperatorInboxNotificationType?,
        status: OperatorInboxNotificationStatus?,
        pageable: Pageable,
    ): Page<OperatorInboxNotification>
}
