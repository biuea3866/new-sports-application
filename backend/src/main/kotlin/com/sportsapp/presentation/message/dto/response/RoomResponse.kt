package com.sportsapp.presentation.message.dto.response

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.vo.RoomType

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
