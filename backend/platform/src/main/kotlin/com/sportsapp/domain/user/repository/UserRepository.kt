package com.sportsapp.domain.user.repository

import com.sportsapp.domain.user.entity.User

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByEmail(email: String): User?

    /** 표시 이름 일괄 조회용 — 목록 화면의 사용자별 단건 조회(N+1)를 막는다. */
    fun findAllBy(userIds: Collection<Long>): List<User>
}
