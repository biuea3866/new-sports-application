package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class NotificationRepositoryImpl(
    private val notificationJpaRepository: NotificationJpaRepository,
) : NotificationRepository {

    override fun save(notification: Notification): Notification =
        notificationJpaRepository.save(notification)

    override fun findById(id: Long): Notification? =
        notificationJpaRepository.findByIdOrNull(id)

    override fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<Notification> =
        notificationJpaRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, status)

    override fun saveAll(notifications: List<Notification>): List<Notification> =
        notificationJpaRepository.saveAll(notifications)

    override fun countUnreadByUserId(userId: Long): Long =
        notificationJpaRepository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId)
}
