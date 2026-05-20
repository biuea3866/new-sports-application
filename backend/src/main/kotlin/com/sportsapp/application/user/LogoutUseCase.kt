package com.sportsapp.application.user

import com.sportsapp.domain.user.AuthDomainService
import org.springframework.stereotype.Service

@Service
class LogoutUseCase(
    private val authDomainService: AuthDomainService,
) {
    fun execute(command: LogoutCommand) {
        authDomainService.logout(command.accessToken, command.userId)
    }
}
