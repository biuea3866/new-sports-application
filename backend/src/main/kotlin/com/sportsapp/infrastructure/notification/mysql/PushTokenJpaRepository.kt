package com.sportsapp.infrastructure.notification.mysql
import com.sportsapp.domain.notification.entity.PushToken
import org.springframework.data.jpa.repository.JpaRepository

interface PushTokenJpaRepository : JpaRepository<PushToken, Long> {
    fun findByTokenAndDeletedAtIsNull(token: String): PushToken?
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<PushToken>
}
