package com.sportsapp.infrastructure.notification.mysql
import com.sportsapp.domain.notification.entity.PushToken
import com.sportsapp.domain.notification.repository.PushTokenRepository
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
