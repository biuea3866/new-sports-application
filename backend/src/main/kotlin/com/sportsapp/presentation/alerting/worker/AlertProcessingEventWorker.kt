package com.sportsapp.presentation.alerting.worker

import com.sportsapp.application.alerting.usecase.ProcessAlertUseCase
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Alert 저장 트랜잭션 커밋 후 LLM 분석(ProcessAlertUseCase)을 비동기로 트리거한다.
 * webhook 요청 스레드와 분리해 Grafana 타임아웃을 회피한다 (TDD.md §Detail Design, ADR-004).
 */
@Component
class AlertProcessingEventWorker(
    private val processAlertUseCase: ProcessAlertUseCase,
) {
    private val log = LoggerFactory.getLogger(AlertProcessingEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProcessingRequested(event: AlertProcessingRequestedEvent) {
        log.info("AlertProcessingEventWorker: processing alertId={}", event.aggregateId)
        try {
            processAlertUseCase.execute(event.aggregateId)
        } catch (e: Exception) {
            log.error("AlertProcessingEventWorker: failed to process alertId={}", event.aggregateId, e)
        }
    }
}
