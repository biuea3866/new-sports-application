package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.RoomCustomRepository
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomRepository
import org.springframework.stereotype.Component

@Component
class RoomRepositoryImpl(
    private val roomJpaRepository: RoomJpaRepository,
    private val lRoomCustomRepository: RoomCustomRepository,
) : RoomRepository {

    override fun save(room: Room): Room = roomJpaRepository.save(room)

    override fun findById(id: Long): Room? = roomJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room? =
        lRoomCustomRepository.findDirectRoomByParticipantIds(userIdA, userIdB)

    override fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room> =
        lRoomCustomRepository.findMyRoomsByKeyword(userId, keyword)
}
