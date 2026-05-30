package com.sportsapp.application.notification

import com.sportsapp.domain.notification.PushTokenDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterPushTokenUseCase(
    private val pushTokenDomainService: PushTokenDomainService,
) {
    @Transactional
    fun execute(command: RegisterPushTokenCommand): PushTokenResponse {
        val pushToken = pushTokenDomainService.register(command.userId, command.token, command.platform)
        return PushTokenResponse.of(pushToken)
    }
}
