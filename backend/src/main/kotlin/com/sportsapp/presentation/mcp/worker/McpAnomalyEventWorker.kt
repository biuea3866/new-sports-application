package com.sportsapp.presentation.mcp.worker

import com.sportsapp.application.mcp.dto.PersistAnomalyEventCommand
import com.sportsapp.application.mcp.usecase.PersistAnomalyEventUseCase
import com.sportsapp.domain.mcp.event.McpAnomalyDetectedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class McpAnomalyEventWorker(
    private val persistAnomalyEventUseCase: PersistAnomalyEventUseCase,
) {
    private val log = LoggerFactory.getLogger(McpAnomalyEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAnomalyDetected(event: McpAnomalyDetectedEvent) {
        log.info("McpAnomalyEventWorker: persisting anomaly for tokenId={}, eventId={}", event.tokenId, event.eventId)
        try {
            persistAnomalyEventUseCase.execute(PersistAnomalyEventCommand.of(event))
        } catch (e: Exception) {
            log.error("McpAnomalyEventWorker: failed to persist anomaly eventId={}, tokenId={}", event.eventId, event.tokenId, e)
        }
    }
}
