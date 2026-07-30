package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.RolePermission
import org.springframework.data.jpa.repository.JpaRepository

interface RolePermissionJpaRepository : JpaRepository<RolePermission, Long> {
    fun findByRoleIdAndDeletedAtIsNull(roleId: Long): List<RolePermission>
}
