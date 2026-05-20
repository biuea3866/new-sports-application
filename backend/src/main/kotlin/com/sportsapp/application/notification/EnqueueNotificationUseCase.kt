package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EnqueueNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: EnqueueNotificationCommand) {
        notificationDomainService.enqueueOrSkip(
            eventId = command.eventId,
            userId = command.recipientUserId,
            channel = command.channel,
            templateId = command.templateId,
            payload = command.payload,
        )
    }
}
