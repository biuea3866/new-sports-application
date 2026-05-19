package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.RolePermission
import org.springframework.data.jpa.repository.JpaRepository

interface RolePermissionJpaRepository : JpaRepository<RolePermission, Long> {
    fun findByRoleIdAndDeletedAtIsNull(roleId: Long): List<RolePermission>
}
