package com.sportsapp.domain.user

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.exceptions.DuplicateEmailException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserDomainService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun register(email: String, rawPassword: String): User {
        if (userRepository.findByEmail(email) != null) throw DuplicateEmailException(email)
        val user = User.create(email, passwordEncoder.encode(rawPassword))
        val savedUser = userRepository.save(user)
        val defaultRole = roleRepository.findByName("USER")
            ?: throw ResourceNotFoundException("Role", "USER")
        if (!userRoleRepository.existsByUserIdAndRoleId(savedUser.id, defaultRole.id)) {
            userRoleRepository.save(UserRole(userId = savedUser.id, roleId = defaultRole.id))
        }
        return savedUser
    }

    fun findByEmail(email: String): User =
        userRepository.findByEmail(email) ?: throw ResourceNotFoundException("User", email)

    fun findByIdWithRoles(id: Long): User =
        userRepository.findByIdWithRoles(id) ?: throw ResourceNotFoundException("User", id)

    fun getRolesForUser(userId: Long): List<Role> {
        val userRoles = userRoleRepository.findActiveByUserId(userId)
        return userRoles.mapNotNull { userRole ->
            roleRepository.findById(userRole.roleId)
        }
    }
}
