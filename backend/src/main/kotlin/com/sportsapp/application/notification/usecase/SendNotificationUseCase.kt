package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.dto.SendNotificationCommand
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.service.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SendNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: SendNotificationCommand): NotificationResult =
        notificationDomainService.sendWithTemplate(
            userId = command.userId,
            channel = command.channel,
            templateId = command.templateId,
            payload = command.payload,
        )
}
