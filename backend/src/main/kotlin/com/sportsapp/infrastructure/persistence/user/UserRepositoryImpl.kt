package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.User
import com.sportsapp.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    override fun save(user: User): User =
        userJpaRepository.save(user)

    override fun findById(id: Long): User? =
        userJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByEmail(email: String): User? =
        userJpaRepository.findByEmailAndDeletedAtIsNull(email)

    override fun findByIdWithRoles(id: Long): User? =
        userJpaRepository.findDistinctByIdAndDeletedAtIsNull(id)
}
