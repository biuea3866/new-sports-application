package com.sportsapp.application.notification.usecase

import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationChannel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetUnreadCountUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): Long =
        notificationDomainService.countUnread(userId, NotificationChannel.IN_APP)
}
