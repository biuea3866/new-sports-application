package com.sportsapp.domain.community.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 커뮤니티 멤버가 ACTIVE 로 전이(즉시 가입 또는 승인)될 때 적재되는 이벤트 (TDD FR-2).
 * 전용 그룹 채팅방 자동 참여(FR-5)는 별도 컨슈머의 책임이다.
 */
class CommunityMemberJoinedEvent(
    memberId: Long,
    val communityId: Long,
    val userId: Long,
) : AbstractDomainEvent(aggregateId = memberId)
