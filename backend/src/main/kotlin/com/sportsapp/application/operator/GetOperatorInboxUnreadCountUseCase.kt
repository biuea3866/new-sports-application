package com.sportsapp.application.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationDomainService
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
