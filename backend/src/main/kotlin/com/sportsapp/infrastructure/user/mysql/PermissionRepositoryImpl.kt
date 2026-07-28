package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.Permission
import com.sportsapp.domain.user.repository.PermissionRepository
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
