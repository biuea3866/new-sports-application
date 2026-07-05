package com.sportsapp.domain.user.service

import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.dto.UserWithRoles
import com.sportsapp.domain.user.entity.Role
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.exception.DuplicateEmailException
import com.sportsapp.domain.user.repository.RoleRepository
import com.sportsapp.domain.user.repository.UserCustomRepository
import com.sportsapp.domain.user.repository.UserRepository
import com.sportsapp.domain.user.repository.UserRoleRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserDomainService(
    private val userRepository: UserRepository,
    private val userCustomRepository: UserCustomRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun register(email: String, rawPassword: String): User {
        if (userRepository.findByEmail(email) != null) throw DuplicateEmailException(email)
        val user = User.create(email, passwordEncoder.encode(rawPassword))
        val savedUser = userRepository.save(user)
        val defaultRole = roleRepository.findByName(UserRoleName.USER)
            ?: throw ResourceNotFoundException("Role", UserRoleName.USER)
        if (!userRoleRepository.existsByUserIdAndRoleId(savedUser.id, defaultRole.id)) {
            userRoleRepository.save(UserRole(userId = savedUser.id, roleId = defaultRole.id, grantedBy = null))
        }
        return savedUser
    }

    fun findById(userId: Long): User =
        userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)

    fun findByEmail(email: String): User =
        userRepository.findByEmail(email) ?: throw ResourceNotFoundException("User", email)

    fun findByIdWithRoles(userId: Long): UserWithRoles =
        userCustomRepository.findByIdWithRoles(userId) ?: throw ResourceNotFoundException("User", userId)

    fun getRolesForUser(userId: Long): List<Role> {
        val userRoles = userRoleRepository.findActiveByUserId(userId)
        return userRoles.mapNotNull { userRole ->
            roleRepository.findById(userRole.roleId)
        }
    }

    fun assignRole(adminId: Long, userId: Long, roleName: String) {
        val user = getUser(userId)
        val role = getRole(roleName)
        val activeRoles = userRoleRepository.findActiveByUserId(userId)
        user.validateNoDuplicateRole(role.id, activeRoles.map { it.roleId }.toSet())
        userRoleRepository.save(UserRole(userId = userId, roleId = role.id, grantedBy = adminId))
    }

    fun revokeRole(adminId: Long, userId: Long, roleName: String) {
        val user = getUser(userId)
        val role = getRole(roleName)
        user.validateCanRevokeAdminRole(
            targetRole = UserRoleName.fromNameOrNull(roleName),
            requesterId = adminId,
        )
        val activeRoles = userRoleRepository.findActiveByUserId(userId)
        user.validateHasMinimumOneRole(activeRoles.size)
        userRoleRepository.findActiveByUserIdAndRoleId(userId, role.id)
            ?: throw ResourceNotFoundException("UserRole", "$userId/$roleName")
        userRoleRepository.softDeleteByUserIdAndRoleId(userId, role.id, adminId)
    }

    fun listUsers(
        emailKeyword: String?,
        roleName: String?,
        pageable: Pageable,
    ): Page<UserWithRoles> =
        userCustomRepository.findAllWithRoles(
            emailKeyword = emailKeyword,
            roleName = roleName,
            pageable = pageable,
        )

    private fun getUser(userId: Long): User =
        userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)

    private fun getRole(roleName: String): Role =
        roleRepository.findByName(roleName) ?: throw ResourceNotFoundException("Role", roleName)
}
