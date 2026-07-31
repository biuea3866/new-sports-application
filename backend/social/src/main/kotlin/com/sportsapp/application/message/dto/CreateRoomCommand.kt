package com.sportsapp.application.message.dto

data class CreateRoomCommand(
    val requestUserId: Long,
    val participantIds: List<Long>,
    val name: String?,
)
