package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.Role
import com.sportsapp.domain.user.repository.RoleRepository
import org.springframework.stereotype.Component

@Component
class RoleRepositoryImpl(
    private val roleJpaRepository: RoleJpaRepository,
) : RoleRepository {

    override fun findById(id: Long): Role? =
        roleJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByName(name: String): Role? =
        roleJpaRepository.findByNameAndDeletedAtIsNull(name)

    override fun findAll(): List<Role> =
        roleJpaRepository.findAllByDeletedAtIsNull()
}
