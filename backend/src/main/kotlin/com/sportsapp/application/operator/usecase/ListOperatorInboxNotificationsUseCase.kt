package com.sportsapp.application.operator.usecase

import com.sportsapp.application.operator.dto.ListOperatorInboxNotificationsCommand
import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import com.sportsapp.application.operator.dto.OperatorInboxNotificationPageResponse
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
