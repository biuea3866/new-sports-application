package com.sportsapp.application.community.dto

import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.common.vo.SportCategory
import java.time.ZonedDateTime

/**
 * TDD "응답 DTO 필드 스키마 — CommunityResponse". Controller가 그대로 반환한다.
 */
data class CommunityResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val visibility: CommunityVisibility,
    val sportCategory: SportCategory,
    val hostUserId: Long,
    val memberCount: Int,
    val roomId: Long?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(community: Community, memberCount: Int, roomId: Long?): CommunityResponse = CommunityResponse(
            id = community.id,
            name = community.name,
            description = community.description,
            visibility = community.visibility,
            sportCategory = community.sportCategory,
            hostUserId = community.currentHostUserId,
            memberCount = memberCount,
            roomId = roomId,
            createdAt = community.createdAt,
        )
    }
}
