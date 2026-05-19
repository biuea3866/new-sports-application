package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.User
import com.sportsapp.domain.user.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(nullable = false, unique = true, length = 320)
    val email: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    val passwordHash: String,
    @Column(nullable = false, length = 20)
    val status: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    val roles: MutableList<RoleJpaEntity> = mutableListOf(),
) {
    fun toDomain(): User {
        val user = User(
            id = id,
            email = email,
            passwordHash = passwordHash,
            status = UserStatus.valueOf(status),
            createdAt = createdAt,
        )
        roles.forEach { user.assignRole(it.toDomain()) }
        return user
    }

    companion object {
        fun fromDomain(user: User, roleEntities: List<RoleJpaEntity> = emptyList()): UserJpaEntity =
            UserJpaEntity(
                id = user.id,
                email = user.email,
                passwordHash = user.passwordHash,
                status = user.status.name,
                createdAt = user.createdAt,
                roles = roleEntities.toMutableList(),
            )
    }
}
