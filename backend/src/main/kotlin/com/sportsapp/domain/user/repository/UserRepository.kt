package com.sportsapp.domain.user.repository

import com.sportsapp.domain.user.entity.User

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByEmail(email: String): User?
}
