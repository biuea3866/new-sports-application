package com.sportsapp.application.user

import com.sportsapp.domain.user.AuthDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LoginUseCase(
    private val authDomainService: AuthDomainService,
) {
    @Transactional
    fun execute(command: LoginCommand): LoginResponse {
        val tokenPair = authDomainService.authenticate(command.email, command.rawPassword)
        return LoginResponse.of(tokenPair)
    }
}
