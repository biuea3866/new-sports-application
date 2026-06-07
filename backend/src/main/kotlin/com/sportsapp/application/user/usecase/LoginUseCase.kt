package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.LoginCommand
import com.sportsapp.domain.user.service.AuthDomainService
import com.sportsapp.domain.user.vo.TokenPair
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LoginUseCase(
    private val authDomainService: AuthDomainService,
) {
    @Transactional
    fun execute(command: LoginCommand): TokenPair =
        authDomainService.authenticate(command.email, command.rawPassword)
}
