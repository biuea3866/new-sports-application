package com.sportsapp.domain.notification

interface PushTokenRepository {
    fun save(pushToken: PushToken): PushToken
    fun findByToken(token: String): PushToken?
    fun findActiveByUserId(userId: Long): List<PushToken>
}
