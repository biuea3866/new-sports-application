package com.sportsapp.domain.notification.repository
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.entity.NotificationStatus
interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findById(id: Long): Notification?
    fun findByEventId(eventId: String): Notification?
    fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<Notification>
    fun saveAll(notifications: List<Notification>): List<Notification>
    fun countUnreadByUserId(userId: Long): Long
}
