package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.RoomParticipant
import com.sportsapp.domain.message.RoomParticipantCustomRepository
import com.sportsapp.domain.message.RoomParticipantRepository
import org.springframework.stereotype.Component

@Component
class RoomParticipantRepositoryImpl(
    private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    private val roomParticipantCustomRepository: RoomParticipantCustomRepository,
) : RoomParticipantRepository {

    override fun save(roomParticipant: RoomParticipant): RoomParticipant =
        roomParticipantJpaRepository.save(roomParticipant)

    override fun findById(id: Long): RoomParticipant? =
        roomParticipantJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findActiveByRoomId(roomId: Long): List<RoomParticipant> =
        roomParticipantCustomRepository.findActiveByRoomId(roomId)

    override fun existsByRoomIdAndUserId(roomId: Long, userId: Long): Boolean =
        roomParticipantCustomRepository.existsByRoomIdAndUserId(roomId, userId)

    override fun findActiveByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant? =
        roomParticipantCustomRepository.findActiveByRoomIdAndUserId(roomId, userId)
}
