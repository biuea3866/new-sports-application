package com.sportsapp.application.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus

data class UpdateOperatorInboxNotificationStatusCommand(
    val notificationId: Long,
    val recipientUserId: Long,
    val targetStatus: OperatorInboxNotificationStatus,
)
