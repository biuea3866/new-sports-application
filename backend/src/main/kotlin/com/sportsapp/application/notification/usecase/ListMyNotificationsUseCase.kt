package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.ListMyNotificationsCommand
import com.sportsapp.application.notification.dto.NotificationPageResult
import com.sportsapp.domain.notification.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyNotificationsUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListMyNotificationsCommand): NotificationPageResult {
        val page = notificationDomainService.listMyNotifications(
            userId = command.userId,
            onlyUnread = command.onlyUnread,
            page = command.page,
            size = command.size,
        )
        return NotificationPageResult.of(page)
    }
}
