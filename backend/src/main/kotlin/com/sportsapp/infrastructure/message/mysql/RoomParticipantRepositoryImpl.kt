package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.repository.RoomParticipantCustomRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import java.time.ZonedDateTime
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

    override fun findExpiredGuestsBefore(threshold: ZonedDateTime): List<RoomParticipant> =
        roomParticipantCustomRepository.findExpiredGuestsBefore(threshold)

    override fun findActiveByUserId(userId: Long): List<RoomParticipant> =
        roomParticipantCustomRepository.findActiveByUserId(userId)
}
