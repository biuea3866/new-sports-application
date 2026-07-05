package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.vo.RoomContextType

interface RoomRepository {
    fun save(room: Room): Room
    fun findById(id: Long): Room?
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?
    fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room>
    fun findByContext(contextType: RoomContextType, contextId: Long): Room?
}
