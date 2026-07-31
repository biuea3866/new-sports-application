package com.sportsapp.application.operator.dto

import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType

data class ListOperatorInboxNotificationsCommand(
    val recipientUserId: Long,
    val typeFilter: OperatorInboxNotificationType?,
    val statusFilter: OperatorInboxNotificationStatus?,
    val page: Int,
    val size: Int,
)
