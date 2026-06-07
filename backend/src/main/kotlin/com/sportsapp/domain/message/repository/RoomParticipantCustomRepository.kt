package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.RoomParticipant

interface RoomParticipantCustomRepository {
    fun findActiveByRoomId(roomId: Long): List<RoomParticipant>
    fun existsByRoomIdAndUserId(roomId: Long, userId: Long): Boolean
    fun findActiveByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant?
}
