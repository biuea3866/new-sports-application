package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.EnqueueNotificationCommand
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EnqueueNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    private val logger = LoggerFactory.getLogger(EnqueueNotificationUseCase::class.java)

    @Transactional
    fun execute(command: EnqueueNotificationCommand) {
        val notification = notificationDomainService.enqueueOrSkip(
            eventId = command.eventId,
            userId = command.recipientUserId,
            channel = command.channel,
            templateId = command.templateId,
            payload = command.payload,
        )
        if (notification?.status == NotificationStatus.FAILED) {
            logger.warn(
                "Notification dispatch failed: eventId={}, userId={}, channel={}, templateId={}",
                command.eventId,
                command.recipientUserId,
                command.channel,
                command.templateId,
            )
        }
    }
}
