package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.PushToken
import com.sportsapp.domain.notification.PushTokenRepository
import org.springframework.stereotype.Component

@Component
class PushTokenRepositoryImpl(
    private val pushTokenJpaRepository: PushTokenJpaRepository,
) : PushTokenRepository {

    override fun save(pushToken: PushToken): PushToken =
        pushTokenJpaRepository.save(pushToken)

    override fun findByToken(token: String): PushToken? =
        pushTokenJpaRepository.findByTokenAndDeletedAtIsNull(token)

    override fun findActiveByUserId(userId: Long): List<PushToken> =
        pushTokenJpaRepository.findByUserIdAndDeletedAtIsNull(userId)
}
