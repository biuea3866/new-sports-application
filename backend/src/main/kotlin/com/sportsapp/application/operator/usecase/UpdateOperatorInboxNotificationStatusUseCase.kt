package com.sportsapp.application.operator.usecase

import com.sportsapp.application.operator.dto.UpdateOperatorInboxNotificationStatusCommand
import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import com.sportsapp.presentation.operator.dto.response.OperatorInboxNotificationResponse
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
