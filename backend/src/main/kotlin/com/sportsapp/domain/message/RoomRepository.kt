package com.sportsapp.domain.message

interface RoomRepository {
    fun save(room: Room): Room
    fun findById(id: Long): Room?
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?
    fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room>
}
