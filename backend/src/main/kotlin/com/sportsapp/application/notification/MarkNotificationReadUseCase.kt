package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MarkNotificationReadUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: MarkNotificationReadCommand): NotificationResponse {
        return notificationDomainService.markRead(
            notificationId = command.notificationId,
            userId = command.userId,
        )
    }
}
