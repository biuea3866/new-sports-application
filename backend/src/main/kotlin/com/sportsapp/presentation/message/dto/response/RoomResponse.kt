package com.sportsapp.presentation.message.dto.response

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView
import com.sportsapp.domain.message.vo.RoomType
import java.time.ZonedDateTime

data class RoomResponse(
    val id: Long,
    val type: RoomType,
    val name: String?,
    val contextType: RoomContextType?,
    val lastMessagePreview: String?,
    val lastMessageAt: ZonedDateTime?,
) {
    companion object {
        private const val PREVIEW_MAX_LENGTH = 50

        fun of(room: Room): RoomResponse = RoomResponse(
            id = room.id,
            type = room.type,
            name = room.name,
            contextType = room.contextType,
            lastMessagePreview = null,
            lastMessageAt = room.lastMessageAt,
        )

        fun of(view: RoomListView): RoomResponse = RoomResponse(
            id = view.roomId,
            type = view.type,
            name = view.name,
            contextType = view.contextType,
            lastMessagePreview = view.lastMessageContent?.take(PREVIEW_MAX_LENGTH),
            lastMessageAt = view.lastMessageAt,
        )
    }
}
