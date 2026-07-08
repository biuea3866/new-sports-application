package com.sportsapp.infrastructure.operator.mysql

import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
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
