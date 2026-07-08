package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.RegisterUserCommand
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterUserUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: RegisterUserCommand): User =
        userDomainService.register(command.email, command.rawPassword)
}
