package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.repository.MessageCustomRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import org.springframework.stereotype.Service

/**
 * 읽음 커서·안읽은 수 (TDD FR-7/9). 참여자별 `lastReadMessageId` 를 전진(forward-only)시키고,
 * 그 기준으로 안읽은 메시지 수를 집계한다.
 */
@Service
class ReadCursorDomainService(
    private val roomParticipantRepository: RoomParticipantRepository,
    private val messageCustomRepository: MessageCustomRepository,
    private val messageBroadcastGateway: MessageBroadcastGateway,
) {

    fun markRead(roomId: Long, userId: Long, lastReadMessageId: Long): RoomParticipant {
        val participant = requireActiveParticipant(roomId, userId)
        participant.markReadUpTo(lastReadMessageId)
        val saved = roomParticipantRepository.save(participant)
        val currentLastRead = requireNotNull(saved.currentLastReadMessageId)
        messageBroadcastGateway.broadcastRead(roomId, ReadEvent(userId = userId, lastReadMessageId = currentLastRead))
        return saved
    }

    fun unreadCount(roomId: Long, userId: Long): Long {
        val participant = requireActiveParticipant(roomId, userId)
        return messageCustomRepository.countUnread(roomId, afterMessageIdOf(participant), userId)
    }

    fun unreadForMyRooms(userId: Long): Map<Long, Long> =
        roomParticipantRepository.findActiveByUserId(userId).associate { participant ->
            participant.room.id to messageCustomRepository.countUnread(participant.room.id, afterMessageIdOf(participant), userId)
        }

    private fun requireActiveParticipant(roomId: Long, userId: Long): RoomParticipant =
        roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, userId)
            ?: throw NotRoomParticipantException(userId, roomId)

    private fun afterMessageIdOf(participant: RoomParticipant): Long = participant.currentLastReadMessageId ?: 0L
}
