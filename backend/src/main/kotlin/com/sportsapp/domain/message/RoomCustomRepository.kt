package com.sportsapp.domain.message

interface RoomCustomRepository {
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?
    fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room>
}
