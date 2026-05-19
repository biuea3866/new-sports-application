package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationStatus
import org.springframework.stereotype.Component

@Component
class NotificationRepositoryImpl(
    private val notificationJpaRepository: NotificationJpaRepository,
) : NotificationRepository {

    override fun save(notification: Notification): Notification =
        notificationJpaRepository.save(NotificationEntity.fromDomain(notification)).toDomain()

    override fun findById(id: Long): Notification? =
        notificationJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByUserIdAndStatus(userId: Long, status: NotificationStatus): List<Notification> =
        notificationJpaRepository.findByUserIdAndStatus(userId, status).map { it.toDomain() }
}
