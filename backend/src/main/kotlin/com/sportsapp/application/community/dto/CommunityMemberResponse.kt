package com.sportsapp.application.community.dto

import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.vo.CommunityRole
import com.sportsapp.domain.community.vo.MembershipStatus
import java.time.ZonedDateTime

/**
 * TDD "응답 DTO 필드 스키마 — CommunityMemberResponse". Controller가 그대로 반환한다.
 */
data class CommunityMemberResponse(
    val id: Long,
    val communityId: Long,
    val userId: Long,
    val role: CommunityRole,
    val status: MembershipStatus,
    val joinedAt: ZonedDateTime?,
) {
    companion object {
        fun of(member: CommunityMember): CommunityMemberResponse = CommunityMemberResponse(
            id = member.id,
            communityId = member.communityId,
            userId = member.userId,
            role = member.currentRole,
            status = member.currentStatus,
            joinedAt = member.currentJoinedAt,
        )
    }
}
