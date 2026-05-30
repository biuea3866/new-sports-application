package com.sportsapp.application.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationType

data class ListOperatorInboxNotificationsCommand(
    val recipientUserId: Long,
    val typeFilter: OperatorInboxNotificationType?,
    val statusFilter: OperatorInboxNotificationStatus?,
    val page: Int,
    val size: Int,
)
