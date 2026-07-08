package com.sportsapp.domain.community.repository

import com.sportsapp.domain.community.entity.Community

/**
 * 커뮤니티 영속화 계약 (TDD 인터페이스 시그니처 — 확정).
 */
interface CommunityRepository {
    fun save(community: Community): Community
    fun findById(id: Long): Community?
    fun findPublicByKeyword(keyword: String?): List<Community>
    fun findByMemberUserId(userId: Long): List<Community>
}
