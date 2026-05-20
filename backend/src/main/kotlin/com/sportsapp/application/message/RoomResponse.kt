package com.sportsapp.application.message

import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomType

data class RoomResponse(
    val id: Long,
    val type: RoomType,
    val name: String?,
) {
    companion object {
        fun of(room: Room): RoomResponse = RoomResponse(
            id = room.id,
            type = room.type,
            name = room.name,
        )
    }
}
