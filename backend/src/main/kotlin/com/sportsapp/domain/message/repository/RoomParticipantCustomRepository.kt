package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.RoomParticipant
import java.time.ZonedDateTime

interface RoomParticipantCustomRepository {
    fun findActiveByRoomId(roomId: Long): List<RoomParticipant>
    fun existsByRoomIdAndUserId(roomId: Long, userId: Long): Boolean
    fun findActiveByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant?
    fun findExpiredGuestsBefore(threshold: ZonedDateTime): List<RoomParticipant>
    fun findActiveByUserId(userId: Long): List<RoomParticipant>
}
