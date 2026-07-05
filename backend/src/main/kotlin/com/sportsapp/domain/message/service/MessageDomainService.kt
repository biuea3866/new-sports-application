package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.event.MessageSentEvent
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.TypingEvent
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import java.time.ZonedDateTime
import org.springframework.stereotype.Service

@Service
class MessageDomainService(
    private val roomRepository: RoomRepository,
    private val messageRepository: MessageRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val messageBroadcastGateway: MessageBroadcastGateway,
) {

    fun createDirectRoom(): Room = roomRepository.save(Room.createDirect())

    fun createGroupRoom(name: String, participantIds: List<Long>): Room {
        val room = roomRepository.save(Room.createGroup(name))
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

    fun findMyRooms(userId: Long, keyword: String?): List<Room> =
        roomRepository.findMyRoomsByKeyword(userId, keyword)

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

    fun sendMessage(roomId: Long, userId: Long, content: String): Message {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw NotRoomParticipantException(userId, roomId)
        }
        val message = messageRepository.save(Message.create(room, userId, content))
        room.lastMessageBumpedTo(message.createdAt)
        roomRepository.save(room)
        domainEventPublisher.publish(
            MessageSentEvent(
                messageId = message.id,
                roomId = roomId,
                senderId = userId,
                content = content,
                sentAt = message.createdAt,
            ),
        )
        return message
    }

    fun listMessages(roomId: Long, userId: Long, cursor: String?): List<Message> {
        val room = roomRepository.findById(roomId)
            ?: throw ResourceNotFoundException("Room", roomId)
        room.validateNotDeleted()
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw NotRoomParticipantException(userId, roomId)
        }
        val before = cursor?.let { java.time.ZonedDateTime.parse(it) }
        return messageRepository.findByCursor(roomId, before, PAGE_SIZE)
    }

    /**
     * 커밋된 메시지를 실시간 구독자에게 팬아웃한다 (BE-04).
     * DB 트랜잭션이 필요 없는 순수 팬아웃이라 `presentation.MessageBroadcastEventWorker` 가
     * `BroadcastMessageUseCase` 를 경유해 이 메서드만 호출하고, Gateway 는 여기서만 사용한다.
     */
    fun broadcastMessage(roomId: Long, messageId: Long, senderId: Long, content: String, sentAt: ZonedDateTime) {
        messageBroadcastGateway.broadcast(
            roomId,
            BroadcastMessage(messageId = messageId, userId = senderId, content = content, createdAt = sentAt),
        )
    }

    /**
     * 타이핑 신호를 실시간 구독자에게 팬아웃한다 (BE-04).
     * 영속화가 필요 없는 신호라 `presentation.ChatStompController` 가 `SendTypingUseCase` 를
     * 경유해 이 메서드만 호출한다.
     */
    fun broadcastTyping(roomId: Long, userId: Long, typing: Boolean) {
        messageBroadcastGateway.broadcastTyping(roomId, TypingEvent(userId = userId, typing = typing))
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
