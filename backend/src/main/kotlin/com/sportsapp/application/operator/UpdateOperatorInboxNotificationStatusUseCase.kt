package com.sportsapp.application.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateOperatorInboxNotificationStatusUseCase(
    private val operatorInboxNotificationDomainService: OperatorInboxNotificationDomainService,
) {
    @Transactional
    fun execute(command: UpdateOperatorInboxNotificationStatusCommand): OperatorInboxNotificationResponse {
        val notification = operatorInboxNotificationDomainService.updateStatus(
            notificationId = command.notificationId,
            recipientUserId = command.recipientUserId,
            targetStatus = command.targetStatus,
        )
        return OperatorInboxNotificationResponse.of(notification)
    }
}
