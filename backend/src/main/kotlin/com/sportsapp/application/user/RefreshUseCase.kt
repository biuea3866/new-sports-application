package com.sportsapp.application.user

import com.sportsapp.domain.user.AuthDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefreshUseCase(
    private val authDomainService: AuthDomainService,
) {
    @Transactional
    fun execute(command: RefreshCommand): LoginResponse {
        val tokenPair = authDomainService.refresh(command.userId, command.refreshToken)
        return LoginResponse.of(tokenPair)
    }
}
