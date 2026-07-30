package com.sportsapp.domain.user.repository

import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.user.entity.Role

interface RoleRepository {
    fun findById(id: Long): Role?
    fun findByName(name: String): Role?
    fun findByName(role: UserRoleName): Role? = findByName(role.name)
    fun findAll(): List<Role>
}
