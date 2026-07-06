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

    /** BE-11: 같은 컨텍스트(예: GOODS_PRODUCT)에 구매자별로 여러 방이 있을 수 있어 참여자로 좁혀 조회한다. */
    fun findByContextAndParticipant(contextType: RoomContextType, contextId: Long, participantUserId: Long): Room?
}
