package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.repository.UserRepository
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
}
