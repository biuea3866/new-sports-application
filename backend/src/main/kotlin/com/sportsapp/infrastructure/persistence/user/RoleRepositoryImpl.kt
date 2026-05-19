package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.Role
import com.sportsapp.domain.user.RoleRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class RoleRepositoryImpl(
    private val roleJpaRepository: RoleJpaRepository,
) : RoleRepository {

    override fun findById(id: Long): Role? =
        roleJpaRepository.findByIdOrNull(id)

    override fun findByName(name: String): Role? =
        roleJpaRepository.findByNameAndDeletedAtIsNull(name)

    override fun findAll(): List<Role> =
        roleJpaRepository.findAllByDeletedAtIsNull()
}
