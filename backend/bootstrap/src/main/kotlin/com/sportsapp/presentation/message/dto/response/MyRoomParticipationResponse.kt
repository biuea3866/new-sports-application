package com.sportsapp.presentation.message.dto.response

import com.sportsapp.domain.message.vo.ParticipantType
import com.sportsapp.domain.message.vo.RoomParticipationView
import java.time.ZonedDateTime

data class MyRoomParticipationResponse(
    val roomId: Long,
    val participantType: ParticipantType,
    val canSpeak: Boolean,
    val expiresAt: ZonedDateTime?,
    val isHost: Boolean,
) {
    companion object {
        fun of(view: RoomParticipationView): MyRoomParticipationResponse = MyRoomParticipationResponse(
            roomId = view.roomId,
            participantType = view.participantType,
            canSpeak = view.canSpeak,
            expiresAt = view.expiresAt,
            isHost = view.isHost,
        )
    }
}
