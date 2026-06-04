package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.AssignRoleCommand
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AssignRoleUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: AssignRoleCommand) {
        userDomainService.assignRole(
            adminId = command.adminId,
            userId = command.userId,
            roleName = command.roleName,
        )
    }
}
