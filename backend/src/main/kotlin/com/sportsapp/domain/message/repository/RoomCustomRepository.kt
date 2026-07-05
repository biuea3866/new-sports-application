package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView

interface RoomCustomRepository {
    fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room?

    /**
     * 내가 참여한 방목록 + 각 방의 마지막 메시지 1건을 단일 쿼리 조인으로 조회한다 (BE-12, N+1 회피).
     */
    fun findMyRoomViews(userId: Long, keyword: String?): List<RoomListView>
    fun findByContext(contextType: RoomContextType, contextId: Long): Room?
}
