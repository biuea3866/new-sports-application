package com.sportsapp.application.community.dto

import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.SportCategory

data class CreateCommunityCommand(
    val name: String,
    val description: String?,
    val visibility: CommunityVisibility,
    val sportCategory: SportCategory,
    val hostUserId: Long,
)

data class JoinCommunityCommand(
    val communityId: Long,
    val userId: Long,
)

data class ApproveMemberCommand(
    val communityId: Long,
    val requesterId: Long,
    val targetUserId: Long,
)

data class KickMemberCommand(
    val communityId: Long,
    val requesterId: Long,
    val targetUserId: Long,
)

data class TransferHostCommand(
    val communityId: Long,
    val requesterId: Long,
    val newHostUserId: Long,
)

data class LeaveCommunityCommand(
    val communityId: Long,
    val userId: Long,
)
