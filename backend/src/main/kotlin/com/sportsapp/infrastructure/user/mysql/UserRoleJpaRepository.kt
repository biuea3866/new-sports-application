package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository

interface UserRoleJpaRepository : JpaRepository<UserRole, Long> {
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<UserRole>
    fun existsByUserIdAndRoleIdAndDeletedAtIsNull(userId: Long, roleId: Long): Boolean
    fun findByUserIdAndRoleIdAndDeletedAtIsNull(userId: Long, roleId: Long): UserRole?
}
