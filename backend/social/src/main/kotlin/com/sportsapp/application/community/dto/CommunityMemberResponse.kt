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
    /** 멤버 표시 이름. user 컨텍스트 소유 값이라 application 레이어가 조회해 채운다. */
    val displayName: String,
    val role: CommunityRole,
    val status: MembershipStatus,
    val joinedAt: ZonedDateTime?,
    val isHost: Boolean,
) {
    companion object {
        fun of(member: CommunityMember, displayName: String): CommunityMemberResponse = CommunityMemberResponse(
            id = member.id,
            communityId = member.communityId,
            userId = member.userId,
            displayName = displayName,
            role = member.currentRole,
            status = member.currentStatus,
            joinedAt = member.currentJoinedAt,
            isHost = member.isHost,
        )
    }
}
