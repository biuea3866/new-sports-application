package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.ReconcileLimitedDropsUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 한정판 활성 회차 대사(reconciliation) 스케줄러 (BE-11, Observability·ADR-001 실패 경로).
 *
 * in-process `@Scheduled` — 전용 스케줄러 서버 불필요(TDD 서버 토폴로지).
 * UseCase 경유로만 대사를 수행한다(Repository 직접 호출 금지).
 */
@Component
class DropReconciliationWorker(
    private val reconcileLimitedDropsUseCase: ReconcileLimitedDropsUseCase,
) {
    private val log = LoggerFactory.getLogger(DropReconciliationWorker::class.java)

    @Scheduled(fixedDelayString = "\${app.limited-drop.reconciliation.fixed-delay-millis:60000}")
    fun reconcile() {
        log.debug("DropReconciliationWorker: starting reconciliation")
        reconcileLimitedDropsUseCase.execute()
        log.debug("DropReconciliationWorker: reconciliation completed")
    }
}
