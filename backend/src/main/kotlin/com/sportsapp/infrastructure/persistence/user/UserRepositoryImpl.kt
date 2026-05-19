package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.User
import com.sportsapp.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
    private val roleJpaRepository: RoleJpaRepository,
) : UserRepository {

    override fun save(user: User): User {
        val roleEntities = user.getRoles()
            .mapNotNull { role -> roleJpaRepository.findByIdOrNull(role.id) }
        val entity = UserJpaEntity.fromDomain(user, roleEntities)
        return userJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): User? =
        userJpaRepository.findByIdOrNull(id)?.toDomain()

    override fun findByEmail(email: String): User? =
        userJpaRepository.findByEmail(email).orElse(null)?.toDomain()

    override fun findByIdWithRoles(id: Long): User? =
        userJpaRepository.findByIdOrNull(id)?.toDomain()
}
