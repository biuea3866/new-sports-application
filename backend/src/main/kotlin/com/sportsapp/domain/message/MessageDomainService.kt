package com.sportsapp.domain.message

import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service

@Service
class MessageDomainService(
    private val roomRepository: RoomRepository,
    private val messageRepository: MessageRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {

    fun createDirectRoom(): Room = roomRepository.save(Room.createDirect())

    fun createGroupRoom(name: String, participantIds: List<Long>): Room {
        val room = roomRepository.save(Room.createGroup(name))
        participantIds.forEach { userId ->
            roomParticipantRepository.save(RoomParticipant.create(room.id, userId))
        }
        return room
    }

    fun createOrFindOneToOne(userIdA: Long, userIdB: Long): Room {
        val sortedIdA = minOf(userIdA, userIdB)
        val sortedIdB = maxOf(userIdA, userIdB)
        val existing = roomRepository.findDirectRoomByParticipantIds(sortedIdA, sortedIdB)
        if (existing != null) return existing
        val room = roomRepository.save(Room.createDirect())
        roomParticipantRepository.save(RoomParticipant.create(room.id, sortedIdA))
        roomParticipantRepository.save(RoomParticipant.create(room.id, sortedIdB))
        return room
    }

    fun getRoom(roomId: Long): Room =
        roomRepository.findById(roomId) ?: throw ResourceNotFoundException("Room", roomId)

    fun findMyRooms(userId: Long, keyword: String?): List<Room> =
        roomRepository.findMyRoomsByKeyword(userId, keyword)

    fun joinRoom(roomId: Long, userId: Long): RoomParticipant {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        if (roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw BusinessRuleViolationException("User $userId is already in room $roomId")
        }
        return roomParticipantRepository.save(RoomParticipant.create(roomId, userId))
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

    fun sendMessage(roomId: Long, userId: Long, content: String): Message {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        return messageRepository.save(Message.create(roomId, userId, content))
    }
}
