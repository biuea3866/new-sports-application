package com.sportsapp.infrastructure.persistence.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RoleJpaRepository : JpaRepository<RoleJpaEntity, Long> {
    fun findByName(name: String): Optional<RoleJpaEntity>
}
