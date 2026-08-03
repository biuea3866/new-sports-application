package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.ChangeMyNicknameCommand
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangeMyNicknameUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: ChangeMyNicknameCommand): User =
        userDomainService.changeNickname(command.userId, command.nickname)
}
