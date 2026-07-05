package com.sportsapp.domain.community.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 커뮤니티 멤버가 탈퇴(LEFT) 또는 강퇴(KICKED)될 때 적재되는 이벤트 (TDD FR-3/5).
 * 연결된 그룹 채팅방 자동 퇴장(FR-5)은 별도 컨슈머의 책임이다.
 */
class CommunityMemberLeftEvent(
    memberId: Long,
    val communityId: Long,
    val userId: Long,
) : AbstractDomainEvent(aggregateId = memberId)
