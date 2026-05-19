package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByEmailAndDeletedAtIsNull(email: String): User?
    fun findByIdAndDeletedAtIsNull(id: Long): User?
}
