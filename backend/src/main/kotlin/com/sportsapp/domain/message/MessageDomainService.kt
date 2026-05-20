package com.sportsapp.domain.message

import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException

class MessageDomainService(
    private val roomRepository: RoomRepository,
    private val messageRepository: MessageRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {

    fun createDirectRoom(): Room = roomRepository.save(Room.createDirect())

    fun createGroupRoom(name: String): Room = roomRepository.save(Room.createGroup(name))

    fun joinRoom(roomId: Long, userId: Long): RoomParticipant {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        if (roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw BusinessRuleViolationException("User $userId is already in room $roomId")
        }
        return roomParticipantRepository.save(RoomParticipant.create(roomId, userId))
    }

    fun sendMessage(roomId: Long, userId: Long, content: String): Message {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        return messageRepository.save(Message.create(roomId, userId, content))
    }
}
