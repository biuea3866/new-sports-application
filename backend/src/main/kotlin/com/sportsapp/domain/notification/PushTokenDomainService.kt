package com.sportsapp.domain.notification

import org.springframework.stereotype.Service

@Service
class PushTokenDomainService(
    private val pushTokenRepository: PushTokenRepository,
) {
    fun register(userId: Long, token: String, platform: PushPlatform): PushToken {
        val existing = pushTokenRepository.findByToken(token)
        if (existing != null) {
            existing.reassign(userId, platform)
            return pushTokenRepository.save(existing)
        }
        return pushTokenRepository.save(PushToken.create(userId, token, platform))
    }

    fun tokensOf(userId: Long): List<PushToken> =
        pushTokenRepository.findActiveByUserId(userId)
}
