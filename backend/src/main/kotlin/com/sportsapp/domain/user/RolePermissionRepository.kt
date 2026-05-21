package com.sportsapp.domain.user

interface RolePermissionRepository {
    fun findActiveByRoleId(roleId: Long): List<RolePermission>
}
