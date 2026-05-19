package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.Permission
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "permissions")
class PermissionJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(nullable = false, unique = true, length = 100)
    val name: String,
) {
    fun toDomain(): Permission = Permission(id = id, name = name)
}
