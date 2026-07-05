package com.sportsapp.domain.community.repository

import com.sportsapp.domain.community.entity.Community

/**
 * [CommunityRepository]의 QueryDSL 조회 전용 서브셋 — join·조건부 키워드가 필요한 쿼리.
 * infra 구현은 [com.sportsapp.infrastructure.community.mysql.CommunityCustomRepositoryImpl].
 */
interface CommunityCustomRepository {
    fun findPublicByKeyword(keyword: String?): List<Community>
    fun findByMemberUserId(userId: Long): List<Community>
}
