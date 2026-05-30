package com.sportsapp.application.operator

import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationDomainService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListOperatorInboxNotificationsUseCase(
    private val operatorInboxNotificationDomainService: OperatorInboxNotificationDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListOperatorInboxNotificationsCommand): OperatorInboxNotificationPageResponse {
        val pageable = PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = operatorInboxNotificationDomainService.listByRecipient(
            recipientUserId = command.recipientUserId,
            type = command.typeFilter,
            status = command.statusFilter,
            pageable = pageable,
        )
        return OperatorInboxNotificationPageResponse.of(page)
    }
}
