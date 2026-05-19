package com.sportsapp.domain.user

import com.sportsapp.domain.user.exceptions.DuplicateRoleException
import com.sportsapp.domain.user.exceptions.InvalidEmailException
import java.time.ZonedDateTime

class User(
    val id: Long,
    val email: String,
    var passwordHash: String,
    val status: UserStatus,
    val createdAt: ZonedDateTime,
    private val roles: MutableList<Role> = mutableListOf(),
) {
    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun create(
            email: String,
            passwordHash: String,
            createdAt: ZonedDateTime,
        ): User {
            if (!EMAIL_REGEX.matches(email)) throw InvalidEmailException(email)
            return User(
                id = 0,
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.ACTIVE,
                createdAt = createdAt,
            )
        }
    }

    fun assignRole(role: Role) {
        if (roles.any { it.id == role.id }) throw DuplicateRoleException(role.id)
        roles.add(role)
    }

    fun getRoles(): List<Role> = roles.toList()

    fun canAccess(resourceOwnerId: Long): Boolean = id == resourceOwnerId

    fun changePassword(newPasswordHash: String) {
        passwordHash = newPasswordHash
    }
}
