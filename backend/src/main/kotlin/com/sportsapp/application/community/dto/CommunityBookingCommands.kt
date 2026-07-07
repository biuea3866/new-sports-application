package com.sportsapp.application.community.dto

data class LinkCommunityBookingCommand(
    val communityId: Long,
    val hostUserId: Long,
    val slotId: Long,
)
