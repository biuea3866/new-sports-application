package com.sportsapp.application.operator.usecase

import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import com.sportsapp.application.operator.dto.OperatorInboxUnreadCountResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetOperatorInboxUnreadCountUseCase(
    private val operatorInboxNotificationDomainService: OperatorInboxNotificationDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(recipientUserId: Long): OperatorInboxUnreadCountResponse =
        OperatorInboxUnreadCountResponse(operatorInboxNotificationDomainService.countUnread(recipientUserId))
}
