package com.sportsapp.application.user

import org.springframework.data.domain.Pageable

data class ListUsersCommand(
    val emailKeyword: String?,
    val roleName: String?,
    val pageable: Pageable,
)
