package com.sportsapp.application.operator.dto

import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus

data class UpdateOperatorInboxNotificationStatusCommand(
    val notificationId: Long,
    val recipientUserId: Long,
    val targetStatus: OperatorInboxNotificationStatus,
)
