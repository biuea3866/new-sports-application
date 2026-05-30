package com.sportsapp.application.user

import com.sportsapp.domain.user.UserDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListUsersUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListUsersCommand): Page<ListUsersResponse> =
        userDomainService.listUsers(
            emailKeyword = command.emailKeyword,
            roleName = command.roleName,
            pageable = command.pageable,
        ).map { ListUsersResponse.of(it) }
}
