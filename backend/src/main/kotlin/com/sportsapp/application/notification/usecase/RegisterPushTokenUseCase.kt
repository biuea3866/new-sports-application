package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.dto.PushTokenResult
import com.sportsapp.application.notification.RegisterPushTokenCommand
import com.sportsapp.domain.notification.PushTokenDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterPushTokenUseCase(
    private val pushTokenDomainService: PushTokenDomainService,
) {
    @Transactional
    fun execute(command: RegisterPushTokenCommand): PushTokenResult {
        val pushToken = pushTokenDomainService.register(command.userId, command.token, command.platform)
        return PushTokenResult.of(pushToken)
    }
}
