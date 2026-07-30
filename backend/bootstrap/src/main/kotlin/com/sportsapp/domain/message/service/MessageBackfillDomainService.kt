package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.MessageCustomRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import org.springframework.stereotype.Service

/**
 * 재연결 backfill (TDD FR-10). WebSocket 재연결 후 끊긴 구간의 메시지를 REST 로 채운다.
 * 참여자·만료 가드는 [ReadCursorDomainService] 와 동일하게 `RoomParticipant` 에 위임한다.
 */
@Service
class MessageBackfillDomainService(
    private val roomParticipantRepository: RoomParticipantRepository,
    private val messageCustomRepository: MessageCustomRepository,
) {

    fun backfill(roomId: Long, userId: Long, afterMessageId: Long): List<Message> {
        val participant = requireActiveParticipant(roomId, userId)
        participant.validateNotExpired()
        return messageCustomRepository.findAfter(roomId, afterMessageId, PAGE_SIZE)
    }

    private fun requireActiveParticipant(roomId: Long, userId: Long): RoomParticipant =
        roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, userId)
            ?: throw NotRoomParticipantException(userId, roomId)

    companion object {
        private const val PAGE_SIZE = 30
    }
}
