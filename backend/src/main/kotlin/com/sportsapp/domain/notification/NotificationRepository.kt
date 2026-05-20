package com.sportsapp.domain.notification

interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findById(id: Long): Notification?
    fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<Notification>
    fun saveAll(notifications: List<Notification>): List<Notification>
    fun countUnreadByUserId(userId: Long): Long
}
