package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.RegisterUserCommand
import com.sportsapp.application.user.dto.RegisterUserResponse
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterUserUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: RegisterUserCommand): RegisterUserResponse =
        RegisterUserResponse.of(userDomainService.register(command.email, command.rawPassword, command.nickname))
}
