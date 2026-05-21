package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.Permission
import com.sportsapp.domain.user.PermissionRepository
import org.springframework.stereotype.Component

@Component
class PermissionRepositoryImpl(
    private val permissionJpaRepository: PermissionJpaRepository,
) : PermissionRepository {

    override fun findByName(name: String): Permission? =
        permissionJpaRepository.findByNameAndDeletedAtIsNull(name)
}
