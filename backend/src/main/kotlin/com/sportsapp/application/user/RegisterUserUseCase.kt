package com.sportsapp.application.user

import com.sportsapp.domain.user.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterUserUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: RegisterUserCommand): RegisterUserResponse {
        val user = userDomainService.register(command.email, command.rawPassword)
        return RegisterUserResponse.of(user)
    }
}
