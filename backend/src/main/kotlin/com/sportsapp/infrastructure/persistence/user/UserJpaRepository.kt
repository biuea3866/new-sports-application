package com.sportsapp.infrastructure.persistence.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByEmail(email: String): Optional<UserJpaEntity>
}
