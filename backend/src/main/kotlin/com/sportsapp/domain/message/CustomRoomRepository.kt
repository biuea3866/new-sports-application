package com.sportsapp.domain.message

interface CustomRoomRepository {
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?
    fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room>
}
