package com.sportsapp.infrastructure.user.mysql

import com.sportsapp.domain.user.entity.Role
import org.springframework.data.jpa.repository.JpaRepository

interface RoleJpaRepository : JpaRepository<Role, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Role?
    fun findByNameAndDeletedAtIsNull(name: String): Role?
    fun findAllByDeletedAtIsNull(): List<Role>
}
