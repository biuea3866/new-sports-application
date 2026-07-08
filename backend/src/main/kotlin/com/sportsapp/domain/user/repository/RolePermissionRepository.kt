package com.sportsapp.domain.user.repository

import com.sportsapp.domain.user.entity.RolePermission

interface RolePermissionRepository {
    fun findActiveByRoleId(roleId: Long): List<RolePermission>
}
