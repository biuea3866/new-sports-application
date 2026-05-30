package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.PushToken
import org.springframework.data.jpa.repository.JpaRepository

interface PushTokenJpaRepository : JpaRepository<PushToken, Long> {
    fun findByTokenAndDeletedAtIsNull(token: String): PushToken?
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<PushToken>
}
