package com.sportsapp.domain.message

interface RoomParticipantCustomRepository {
    fun findActiveByRoomId(roomId: Long): List<RoomParticipant>
    fun existsByRoomIdAndUserId(roomId: Long, userId: Long): Boolean
    fun findActiveByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant?
}
