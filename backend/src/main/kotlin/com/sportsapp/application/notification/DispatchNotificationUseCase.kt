package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationDomainService
import org.springframework.stereotype.Service

@Service
class DispatchNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    fun execute(notificationId: Long) {
        notificationDomainService.dispatchById(notificationId)
    }
}
