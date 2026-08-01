package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomListView
import org.springframework.stereotype.Service

/**
 * Room 생명주기(생성·참여·탈퇴·조회) 책임을 분리한 도메인 서비스 (MessageDomainService
 * TooManyFunctions 정리, W1-DEBT-01). 메시지 발송·실시간 브로드캐스트는 [MessageDomainService]가
 * 담당한다.
 */
@Service
class RoomDomainService(
    private val roomRepository: RoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
    private val messageRepository: MessageRepository,
) {

    fun createDirectRoom(): Room = roomRepository.save(Room.createDirect())

    /** 그룹 방을 생성한다 — hostUserId(BE-13)를 지정하면 방장으로 영속한다(미지정 시 방장 없음). */
    fun createGroupRoom(name: String, participantIds: List<Long>, hostUserId: Long? = null): Room {
        val room = roomRepository.save(Room.createGroup(name, hostUserId = hostUserId))
        participantIds.forEach { userId ->
            roomParticipantRepository.save(RoomParticipant.create(room, userId))
        }
        return room
    }

    fun createOrFindOneToOne(userIdA: Long, userIdB: Long): Room {
        val sortedIdA = minOf(userIdA, userIdB)
        val sortedIdB = maxOf(userIdA, userIdB)
        val existing = roomRepository.findDirectRoomByParticipantIds(sortedIdA, sortedIdB)
        if (existing != null) return existing
        val room = roomRepository.save(Room.createDirect())
        roomParticipantRepository.save(RoomParticipant.create(room, sortedIdA))
        roomParticipantRepository.save(RoomParticipant.create(room, sortedIdB))
        return room
    }

    fun getRoom(roomId: Long, userId: Long): Room {
        val room = roomRepository.findById(roomId) ?: throw ResourceNotFoundException("Room", roomId)
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw NotRoomParticipantException(userId, roomId)
        }
        return room
    }

    fun findMyRoomViews(userId: Long, keyword: String?): List<RoomListView> =
        roomRepository.findMyRoomViews(userId, keyword)

    fun joinRoom(roomId: Long, userId: Long): RoomParticipant {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        if (roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw BusinessRuleViolationException("User $userId is already in room $roomId")
        }
        return roomParticipantRepository.save(RoomParticipant.create(room, userId))
    }

    fun leaveRoom(roomId: Long, userId: Long) {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        val participant = roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, userId)
            ?: throw NotRoomParticipantException(userId, roomId)
        participant.softDelete(userId)
        roomParticipantRepository.save(participant)
        val remaining = roomParticipantRepository.findActiveByRoomId(roomId)
        if (remaining.isEmpty()) {
            messageRepository.softDeleteAllByRoomId(roomId, userId)
            room.softDelete(userId)
            roomRepository.save(room)
        }
    }
}
