package com.sportsapp.domain.common

interface PermissionRepository {
    fun findByName(name: String): Permission?
}
