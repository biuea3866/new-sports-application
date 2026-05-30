package com.sportsapp.domain.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserCustomRepository {
    fun findAllWithRoles(
        emailKeyword: String?,
        roleName: String?,
        pageable: Pageable,
    ): Page<UserWithRoles>
}
