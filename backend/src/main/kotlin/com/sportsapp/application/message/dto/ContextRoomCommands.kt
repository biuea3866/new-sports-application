package com.sportsapp.application.message.dto

import com.sportsapp.domain.message.vo.RoomContextType

data class ProvisionContextRoomCommand(
    val contextType: RoomContextType,
    val contextId: Long,
    val name: String?,
    val hostUserId: Long,
)

data class JoinContextRoomCommand(
    val contextType: RoomContextType,
    val contextId: Long,
    val userId: Long,
)

data class LeaveContextRoomCommand(
    val contextType: RoomContextType,
    val contextId: Long,
    val userId: Long,
)
