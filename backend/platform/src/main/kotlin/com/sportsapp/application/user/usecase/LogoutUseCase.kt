package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.LogoutCommand
import com.sportsapp.domain.user.service.AuthDomainService
import org.springframework.stereotype.Service

@Service
class LogoutUseCase(
    private val authDomainService: AuthDomainService,
) {
    fun execute(command: LogoutCommand) {
        authDomainService.logout(command.accessToken, command.userId)
    }
}
