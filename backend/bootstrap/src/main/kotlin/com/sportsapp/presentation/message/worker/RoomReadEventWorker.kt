package com.sportsapp.presentation.message.worker

import com.sportsapp.application.message.dto.BroadcastReadCommand
import com.sportsapp.application.message.usecase.BroadcastReadUseCase
import com.sportsapp.domain.message.event.RoomReadEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * [RoomReadEvent] 를 커밋 이후에만 수신해 [BroadcastReadUseCase] 를 경유해 팬아웃한다 (BE-05, 코드 리뷰 정정).
 * Gateway 는 UseCase 에 직접 노출되지 않고 domain(ReadCursorDomainService) 내부에 캡슐화된다.
 *
 * 트랜잭션이 롤백되면 이 리스너 자체가 호출되지 않으므로 유령 브로드캐스트가 발생하지 않는다
 * (BE-04 `MessageBroadcastEventWorker` 와 동일 구조).
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class RoomReadEventWorker(
    private val broadcastReadUseCase: BroadcastReadUseCase,
) {
    private val log = LoggerFactory.getLogger(RoomReadEventWorker::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRoomRead(event: RoomReadEvent) {
        try {
            broadcastReadUseCase.execute(event.toCommand())
        } catch (exception: Exception) {
            log.error(
                "읽음 브로드캐스트 실패 — roomId={} userId={} — 클라이언트는 폴링(GET /rooms/me/unread)으로 복구",
                event.roomId,
                event.userId,
                exception,
            )
        }
    }

    private fun RoomReadEvent.toCommand(): BroadcastReadCommand =
        BroadcastReadCommand(roomId = roomId, userId = userId, lastReadMessageId = lastReadMessageId)
}
