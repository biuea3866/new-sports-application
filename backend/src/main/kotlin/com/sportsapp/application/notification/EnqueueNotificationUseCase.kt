package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.UnsupportedChannelException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EnqueueNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: EnqueueNotificationCommand) {
        if (command.channel !in SUPPORTED_CHANNELS) throw UnsupportedChannelException(command.channel)
        notificationDomainService.enqueueOrSkip(
            eventId = command.eventId,
            userId = command.recipientUserId,
            channel = command.channel,
            templateId = command.templateId,
            payload = command.payload,
        )
    }

    companion object {
        private val SUPPORTED_CHANNELS = setOf(NotificationChannel.IN_APP)
    }
}
