package com.sportsapp.application.user

import com.sportsapp.domain.user.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyProfileUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetMyProfileCommand): GetMyProfileResponse {
        val user = userDomainService.findById(command.userId)
        return GetMyProfileResponse.of(user)
    }
}
