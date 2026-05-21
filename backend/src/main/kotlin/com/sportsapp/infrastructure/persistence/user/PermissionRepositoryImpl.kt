package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.common.Permission
import com.sportsapp.domain.common.PermissionRepository
import org.springframework.stereotype.Component

@Component
class PermissionRepositoryImpl(
    private val permissionJpaRepository: PermissionJpaRepository,
) : PermissionRepository {

    override fun findByName(name: String): Permission? =
        permissionJpaRepository.findByNameAndDeletedAtIsNull(name)

    override fun findById(id: Long): Permission? =
        permissionJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findAllByIds(ids: List<Long>): List<Permission> =
        permissionJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
}
