package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.dto.MarkNotificationReadCommand
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.service.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MarkNotificationReadUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: MarkNotificationReadCommand): NotificationResult =
        notificationDomainService.markRead(
            notificationId = command.notificationId,
            userId = command.userId,
        )
}
