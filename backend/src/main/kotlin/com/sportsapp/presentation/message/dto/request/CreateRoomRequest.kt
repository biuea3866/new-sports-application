package com.sportsapp.presentation.message.dto.request

import com.sportsapp.application.message.dto.CreateRoomCommand

data class CreateRoomRequest(
    val participantIds: List<Long>,
    val name: String?,
) {
    fun toCommand(requestUserId: Long): CreateRoomCommand = CreateRoomCommand(
        requestUserId = requestUserId,
        participantIds = participantIds,
        name = name,
    )
}
