package com.sportsapp.domain.user.repository

import com.sportsapp.domain.user.entity.Permission

interface PermissionRepository {
    fun findByName(name: String): Permission?
    fun findById(id: Long): Permission?
    fun findAllByIds(ids: List<Long>): List<Permission>
}
