package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.Permission
import org.springframework.data.jpa.repository.JpaRepository

interface PermissionJpaRepository : JpaRepository<Permission, Long> {
    fun findByNameAndDeletedAtIsNull(name: String): Permission?
    fun findByIdAndDeletedAtIsNull(id: Long): Permission?
}
