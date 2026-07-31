package com.sportsapp.domain.message.vo

import java.time.ZonedDateTime

/**
 * "내 방 참여정보" 조회 결과 (BE-14, FE-10 canSpeak/expiresAt 하드코딩 degrade 해소).
 *
 * [RoomParticipationQueryService][com.sportsapp.domain.message.service.RoomParticipationQueryService]가
 * `RoomParticipant`(canSpeak/expiresAt/participantType)와 `Room.isHostedBy`(BE-13)를 조합해 만든다.
 */
data class RoomParticipationView(
    val roomId: Long,
    val participantType: ParticipantType,
    val canSpeak: Boolean,
    val expiresAt: ZonedDateTime?,
    val isHost: Boolean,
)
