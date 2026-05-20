package com.sportsapp.domain.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CustomNotificationRepository {
    fun findByUserIdPaged(userId: Long, onlyUnread: Boolean, pageable: Pageable): Page<Notification>
}
