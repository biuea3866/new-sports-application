package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.NotificationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<NotificationEntity, Long> {
    fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<NotificationEntity>
}
