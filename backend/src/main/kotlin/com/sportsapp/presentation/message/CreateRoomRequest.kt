package com.sportsapp.presentation.message

import com.sportsapp.application.message.CreateRoomCommand

data class CreateRoomRequest(
    val participantIds: List<Long>,
    val name: String?,
) {
    fun toCommand(): CreateRoomCommand = CreateRoomCommand(
        participantIds = participantIds,
        name = name,
    )
}
