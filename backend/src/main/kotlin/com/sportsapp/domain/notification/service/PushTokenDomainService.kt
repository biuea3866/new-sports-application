package com.sportsapp.domain.notification.service
import org.springframework.stereotype.Service
import com.sportsapp.domain.notification.entity.PushPlatform
import com.sportsapp.domain.notification.entity.PushToken
import com.sportsapp.domain.notification.repository.PushTokenRepository

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
