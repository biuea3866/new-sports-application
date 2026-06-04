package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.RevokeRoleCommand
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RevokeRoleUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: RevokeRoleCommand) {
        userDomainService.revokeRole(
            adminId = command.adminId,
            userId = command.userId,
            roleName = command.roleName,
        )
    }
}
