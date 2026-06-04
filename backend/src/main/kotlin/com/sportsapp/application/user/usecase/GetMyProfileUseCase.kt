package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.GetMyProfileCommand
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyProfileUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetMyProfileCommand): User =
        userDomainService.findById(command.userId)
}
