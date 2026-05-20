package com.sportsapp.domain.user

interface RoleRepository {
    fun findById(id: Long): Role?
    fun findByName(name: String): Role?
    fun findAll(): List<Role>
}
