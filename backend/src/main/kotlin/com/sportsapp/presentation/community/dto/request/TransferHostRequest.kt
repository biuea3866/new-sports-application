package com.sportsapp.presentation.community.dto.request

import com.sportsapp.application.community.dto.TransferHostCommand

data class TransferHostRequest(
    val newHostUserId: Long,
) {
    fun toCommand(communityId: Long, requesterId: Long): TransferHostCommand = TransferHostCommand(
        communityId = communityId,
        requesterId = requesterId,
        newHostUserId = newHostUserId,
    )
}
