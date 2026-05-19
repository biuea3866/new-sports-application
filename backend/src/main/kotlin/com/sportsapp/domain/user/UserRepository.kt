package com.sportsapp.domain.user

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByEmail(email: String): User?
    fun findByIdWithRoles(id: Long): User?
}
