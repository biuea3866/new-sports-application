package com.sportsapp.domain.notification.repository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.notification.entity.Notification

interface NotificationCustomRepository {
    fun findByUserIdPaged(userId: Long, onlyUnread: Boolean, pageable: Pageable): Page<Notification>
}
