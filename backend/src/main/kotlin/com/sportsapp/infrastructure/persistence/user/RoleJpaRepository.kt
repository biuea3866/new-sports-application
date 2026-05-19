package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.Role
import org.springframework.data.jpa.repository.JpaRepository

interface RoleJpaRepository : JpaRepository<Role, Long> {
    fun findByNameAndDeletedAtIsNull(name: String): Role?
    fun findAllByDeletedAtIsNull(): List<Role>
}
