package com.sportsapp.domain.user

import com.sportsapp.domain.common.UserRoleName

interface RoleRepository {
    fun findById(id: Long): Role?
    fun findByName(name: String): Role?
    fun findByName(role: UserRoleName): Role? = findByName(role.name)
    fun findAll(): List<Role>
}
