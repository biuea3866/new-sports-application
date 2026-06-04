package com.sportsapp.infrastructure.notification.mysql
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.entity.NotificationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<Notification, Long> {
    fun findByEventId(eventId: String): Notification?
    fun findByUserIdAndStatusAndDeletedAtIsNull(userId: Long, status: NotificationStatus): List<Notification>
    fun countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId: Long): Long
}
