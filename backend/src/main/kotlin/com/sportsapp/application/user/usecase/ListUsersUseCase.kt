package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.ListUsersCommand
import com.sportsapp.domain.user.dto.UserWithRoles
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListUsersUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListUsersCommand): Page<UserWithRoles> =
        userDomainService.listUsers(
            emailKeyword = command.emailKeyword,
            roleName = command.roleName,
            pageable = command.pageable,
        )
}
