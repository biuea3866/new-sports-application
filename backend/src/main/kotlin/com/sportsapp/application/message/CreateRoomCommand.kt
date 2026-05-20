package com.sportsapp.application.message

data class CreateRoomCommand(
    val requestUserId: Long,
    val participantIds: List<Long>,
    val name: String?,
)
