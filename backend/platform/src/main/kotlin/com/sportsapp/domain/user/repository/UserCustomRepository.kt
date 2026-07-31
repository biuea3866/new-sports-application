package com.sportsapp.domain.user.repository

import com.sportsapp.domain.user.dto.UserWithRoles
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserCustomRepository {
    fun findAllWithRoles(
        emailKeyword: String?,
        roleName: String?,
        pageable: Pageable,
    ): Page<UserWithRoles>

    /**
     * User와 활성 역할을 단일 쿼리(LEFT JOIN)로 조회한다.
     * findById + getRolesForUser로 나뉘어 있던 2회 조회를 1회로 통합하기 위한 메서드.
     */
    fun findByIdWithRoles(userId: Long): UserWithRoles?
}
