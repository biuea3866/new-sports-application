package com.sportsapp.domain.user

interface UserRoleRepository {
    fun save(userRole: UserRole): UserRole
    fun findActiveByUserId(userId: Long): List<UserRole>
    fun existsByUserIdAndRoleId(userId: Long, roleId: Long): Boolean
    fun findActiveByUserIdAndRoleId(userId: Long, roleId: Long): UserRole?
    fun softDeleteByUserIdAndRoleId(userId: Long, roleId: Long, deletedBy: Long?)
}
