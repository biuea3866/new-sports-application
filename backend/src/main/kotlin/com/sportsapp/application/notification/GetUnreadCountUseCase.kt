package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetUnreadCountUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): UnreadCountResponse =
        UnreadCountResponse(notificationDomainService.countUnread(userId))
}
