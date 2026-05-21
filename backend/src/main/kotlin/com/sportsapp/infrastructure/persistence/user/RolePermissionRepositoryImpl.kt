package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.RolePermission
import com.sportsapp.domain.user.RolePermissionRepository
import org.springframework.stereotype.Component

@Component
class RolePermissionRepositoryImpl(
    private val rolePermissionJpaRepository: RolePermissionJpaRepository,
) : RolePermissionRepository {

    override fun findActiveByRoleId(roleId: Long): List<RolePermission> =
        rolePermissionJpaRepository.findByRoleIdAndDeletedAtIsNull(roleId)
}
