package com.sportsapp.domain.message

interface RoomParticipantRepository {
    fun save(roomParticipant: RoomParticipant): RoomParticipant
    fun findById(id: Long): RoomParticipant?
    fun findActiveByRoomId(roomId: Long): List<RoomParticipant>
    fun existsByRoomIdAndUserId(roomId: Long, userId: Long): Boolean
}
