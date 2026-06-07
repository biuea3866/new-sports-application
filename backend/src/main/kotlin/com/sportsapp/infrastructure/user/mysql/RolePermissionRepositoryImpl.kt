package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.RolePermission
import com.sportsapp.domain.user.repository.RolePermissionRepository
import org.springframework.stereotype.Component

@Component
class RolePermissionRepositoryImpl(
    private val rolePermissionJpaRepository: RolePermissionJpaRepository,
) : RolePermissionRepository {

    override fun findActiveByRoleId(roleId: Long): List<RolePermission> =
        rolePermissionJpaRepository.findByRoleIdAndDeletedAtIsNull(roleId)
}
