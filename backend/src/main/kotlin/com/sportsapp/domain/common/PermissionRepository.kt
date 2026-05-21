package com.sportsapp.domain.common

interface PermissionRepository {
    fun findByName(name: String): Permission?
    fun findById(id: Long): Permission?
    fun findAllByIds(ids: List<Long>): List<Permission>
}
