package com.sportsapp.application.notification.usecase
import com.sportsapp.domain.notification.service.NotificationDomainService
import org.springframework.stereotype.Service

@Service
class DispatchNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    fun execute(notificationId: Long) {
        notificationDomainService.dispatchById(notificationId)
    }
}
