package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.Role
import com.sportsapp.domain.user.RoleRepository
import org.springframework.stereotype.Component

@Component
class RoleRepositoryImpl(
    private val roleJpaRepository: RoleJpaRepository,
) : RoleRepository {

    override fun findByName(name: String): Role? =
        roleJpaRepository.findByName(name).orElse(null)?.toDomain()

    override fun findAll(): List<Role> =
        roleJpaRepository.findAll().map { it.toDomain() }
}
