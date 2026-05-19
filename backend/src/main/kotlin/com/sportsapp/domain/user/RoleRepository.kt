package com.sportsapp.domain.user

interface RoleRepository {
    fun findByName(name: String): Role?
    fun findAll(): List<Role>
}
