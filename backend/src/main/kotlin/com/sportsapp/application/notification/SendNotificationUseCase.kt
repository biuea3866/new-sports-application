package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SendNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: SendNotificationCommand): NotificationResponse {
        return notificationDomainService.sendWithTemplate(
            userId = command.userId,
            channel = command.channel,
            templateId = command.templateId,
            payload = command.payload,
        )
    }
}
