package com.sportsapp.domain.user

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.user.exceptions.InvalidEmailException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, unique = true, length = 320)
    val email: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: UserStatus,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun create(email: String, passwordHash: String): User {
            if (!EMAIL_REGEX.matches(email)) throw InvalidEmailException(email)
            return User(
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.ACTIVE,
            )
        }
    }

    fun canAccess(resourceOwnerId: Long): Boolean = id == resourceOwnerId

    fun changePassword(newPasswordHash: String) {
        passwordHash = newPasswordHash
    }
}
