package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.UserRole
import org.springframework.data.jpa.repository.JpaRepository

interface UserRoleJpaRepository : JpaRepository<UserRole, Long> {
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<UserRole>
    fun existsByUserIdAndRoleIdAndDeletedAtIsNull(userId: Long, roleId: Long): Boolean
}
