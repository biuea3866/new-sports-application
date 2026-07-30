package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.event.RoomReadEvent
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
    private val domainEventPublisher: DomainEventPublisher,
) {

    /**
     * 읽음 커서 영속화 + [RoomReadEvent] 발행까지만 수행한다 (BE-05 정정).
     * 실시간 브로드캐스트는 이 트랜잭션 안에서 동기 호출하지 않는다 — 커밋 전에 상대에게 팬아웃되면
     * 이후 트랜잭션이 롤백돼도 이미 발송된 "유령 브로드캐스트"가 된다. 실제 팬아웃은
     * presentation `RoomReadEventWorker` 가 `@TransactionalEventListener(AFTER_COMMIT)` 로 수신해
     * [broadcastRead] 를 호출한다 (BE-04 `MessageSentEvent`/`MessageBroadcastEventWorker` 와 동일 구조).
     */
    fun markRead(roomId: Long, userId: Long, lastReadMessageId: Long): RoomParticipant {
        val participant = requireActiveParticipant(roomId, userId)
        participant.markReadUpTo(lastReadMessageId)
        val saved = roomParticipantRepository.save(participant)
        val currentLastRead = requireNotNull(saved.currentLastReadMessageId)
        domainEventPublisher.publish(
            RoomReadEvent(roomId = roomId, userId = userId, lastReadMessageId = currentLastRead),
        )
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

    /**
     * 커밋된 읽음 커서를 실시간 구독자에게 팬아웃한다 (BE-05).
     * DB 트랜잭션이 필요 없는 순수 팬아웃이라 presentation `RoomReadEventWorker` 가
     * `BroadcastReadUseCase` 를 경유해 이 메서드만 호출하고, Gateway 는 여기서만 사용한다.
     */
    fun broadcastRead(roomId: Long, userId: Long, lastReadMessageId: Long) {
        messageBroadcastGateway.broadcastRead(roomId, ReadEvent(userId = userId, lastReadMessageId = lastReadMessageId))
    }

    private fun requireActiveParticipant(roomId: Long, userId: Long): RoomParticipant =
        roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, userId)
            ?: throw NotRoomParticipantException(userId, roomId)

    private fun afterMessageIdOf(participant: RoomParticipant): Long = participant.currentLastReadMessageId ?: 0L
}
