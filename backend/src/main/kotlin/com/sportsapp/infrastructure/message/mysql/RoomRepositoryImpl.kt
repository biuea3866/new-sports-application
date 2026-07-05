package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.repository.RoomCustomRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView
import org.springframework.stereotype.Component

@Component
class RoomRepositoryImpl(
    private val roomJpaRepository: RoomJpaRepository,
    private val roomCustomRepository: RoomCustomRepository,
) : RoomRepository {

    override fun save(room: Room): Room = roomJpaRepository.save(room)

    override fun findById(id: Long): Room? = roomJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room? =
        roomCustomRepository.findDirectRoomByParticipantIds(userIdA, userIdB)

    override fun findMyRoomViews(userId: Long, keyword: String?): List<RoomListView> =
        roomCustomRepository.findMyRoomViews(userId, keyword)

    override fun findByContext(contextType: RoomContextType, contextId: Long): Room? =
        roomCustomRepository.findByContext(contextType, contextId)
}
