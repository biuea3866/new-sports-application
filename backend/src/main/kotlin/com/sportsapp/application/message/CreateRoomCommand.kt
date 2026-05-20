package com.sportsapp.application.message

data class CreateRoomCommand(
    val participantIds: List<Long>,
    val name: String?,
)
