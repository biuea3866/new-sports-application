package com.sportsapp.domain.user

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.time.ZonedDateTime

class UserDomainService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
) {
    fun register(email: String, passwordHash: String): User {
        val user = User.create(email, passwordHash, ZonedDateTime.now())
        val savedUser = userRepository.save(user)
        val defaultRole = roleRepository.findByName("USER")
            ?: throw ResourceNotFoundException("Role", "USER")
        savedUser.assignRole(defaultRole)
        return userRepository.save(savedUser)
    }

    fun findByEmail(email: String): User =
        userRepository.findByEmail(email) ?: throw ResourceNotFoundException("User", email)

    fun findByIdWithRoles(id: Long): User =
        userRepository.findByIdWithRoles(id) ?: throw ResourceNotFoundException("User", id)
}
