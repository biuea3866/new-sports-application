package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByEmailAndDeletedAtIsNull(email: String): User?
    fun findByIdAndDeletedAtIsNull(id: Long): User?
}
