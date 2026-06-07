package com.sportsapp.domain.notification.repository
import com.sportsapp.domain.notification.entity.PushToken
interface PushTokenRepository {
    fun save(pushToken: PushToken): PushToken
    fun findByToken(token: String): PushToken?
    fun findActiveByUserId(userId: Long): List<PushToken>
}
