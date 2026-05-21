package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.UserRole
import com.sportsapp.domain.user.UserRoleRepository
import org.springframework.stereotype.Component

@Component
class UserRoleRepositoryImpl(
    private val userRoleJpaRepository: UserRoleJpaRepository,
) : UserRoleRepository {

    override fun save(userRole: UserRole): UserRole =
        userRoleJpaRepository.save(userRole)

    override fun findActiveByUserId(userId: Long): List<UserRole> =
        userRoleJpaRepository.findByUserIdAndDeletedAtIsNull(userId)

    override fun existsByUserIdAndRoleId(userId: Long, roleId: Long): Boolean =
        userRoleJpaRepository.existsByUserIdAndRoleIdAndDeletedAtIsNull(userId, roleId)

    override fun findActiveByUserIdAndRoleId(userId: Long, roleId: Long): UserRole? =
        userRoleJpaRepository.findByUserIdAndRoleIdAndDeletedAtIsNull(userId, roleId)

    override fun softDeleteByUserIdAndRoleId(userId: Long, roleId: Long, deletedBy: Long?) {
        val userRole = userRoleJpaRepository.findByUserIdAndRoleIdAndDeletedAtIsNull(userId, roleId) ?: return
        userRole.softDelete(deletedBy)
        userRoleJpaRepository.save(userRole)
    }
}
