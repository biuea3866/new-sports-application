package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.Room

interface RoomCustomRepository {
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?
    fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room>
}
