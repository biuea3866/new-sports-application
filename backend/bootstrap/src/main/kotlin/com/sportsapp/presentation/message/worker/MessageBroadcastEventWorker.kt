package com.sportsapp.presentation.message.worker

import com.sportsapp.application.message.dto.BroadcastMessageCommand
import com.sportsapp.application.message.usecase.BroadcastMessageUseCase
import com.sportsapp.domain.message.event.MessageSentEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * [MessageSentEvent] 를 커밋 이후에만 수신해 [BroadcastMessageUseCase] 를 경유해 팬아웃한다 (FR-6).
 * Gateway 는 UseCase 에 직접 노출되지 않고 domain(MessageDomainService) 내부에 캡슐화된다.
 *
 * 트랜잭션이 롤백되면 이 리스너 자체가 호출되지 않으므로 유령 브로드캐스트가 발생하지 않는다.
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class MessageBroadcastEventWorker(
    private val broadcastMessageUseCase: BroadcastMessageUseCase,
) {
    private val log = LoggerFactory.getLogger(MessageBroadcastEventWorker::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleMessageSent(event: MessageSentEvent) {
        try {
            broadcastMessageUseCase.execute(event.toCommand())
        } catch (exception: Exception) {
            log.error(
                "실시간 브로드캐스트 실패 — roomId={} messageId={} — 클라이언트는 backfill 로 복구",
                event.roomId,
                event.aggregateId,
                exception,
            )
        }
    }

    private fun MessageSentEvent.toCommand(): BroadcastMessageCommand =
        BroadcastMessageCommand(
            roomId = roomId,
            messageId = aggregateId,
            senderId = senderId,
            content = content,
            sentAt = sentAt,
        )
}
