package com.sportsapp.domain.notification

interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findById(id: Long): Notification?
    fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<Notification>
}
