package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView

interface RoomRepository {
    fun save(room: Room): Room
    fun findById(id: Long): Room?
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?
    fun findMyRoomViews(userId: Long, keyword: String?): List<RoomListView>
    fun findByContext(contextType: RoomContextType, contextId: Long): Room?
}
