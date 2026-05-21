package com.sportsapp.domain.user

interface PermissionRepository {
    fun findByName(name: String): Permission?
}
