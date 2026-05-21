package com.sportsapp.application.user

import com.sportsapp.domain.user.AuthDomainService
import org.springframework.stereotype.Service

@Service
class RefreshUseCase(
    private val authDomainService: AuthDomainService,
) {
    fun execute(command: RefreshCommand): LoginResponse {
        val tokenPair = authDomainService.refresh(command.refreshToken)
        return LoginResponse.of(tokenPair)
    }
}
