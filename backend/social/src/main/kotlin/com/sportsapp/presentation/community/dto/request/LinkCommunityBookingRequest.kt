package com.sportsapp.presentation.community.dto.request

import com.sportsapp.application.community.dto.LinkCommunityBookingCommand

data class LinkCommunityBookingRequest(
    val slotId: Long,
) {
    fun toCommand(communityId: Long, hostUserId: Long): LinkCommunityBookingCommand = LinkCommunityBookingCommand(
        communityId = communityId,
        hostUserId = hostUserId,
        slotId = slotId,
    )
}
