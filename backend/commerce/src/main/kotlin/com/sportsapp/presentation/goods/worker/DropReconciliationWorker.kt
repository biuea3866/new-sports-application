package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.ReconcileLimitedDropsUseCase
import com.sportsapp.domain.common.exceptions.RedisLockException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 한정판 활성 회차 대사(reconciliation) 스케줄러 (BE-11, Observability·ADR-001 실패 경로).
 *
 * in-process `@Scheduled` — 전용 스케줄러 서버 불필요(TDD 서버 토폴로지).
 * UseCase 경유로만 대사를 수행한다(Repository 직접 호출 금지).
 *
 * 부팅 직후 즉시 실행되지 않도록 초기 지연을 두고, Redis 장애(DataAccessException·RedisLockException)는
 * fail-open으로 삼켜 스케줄러 스레드가 죽지 않게 한다 — 대사는 운영 지표(Observability)일 뿐 서비스
 * 가용성에 영향을 주면 안 된다.
 */
@Component
class DropReconciliationWorker(
    private val reconcileLimitedDropsUseCase: ReconcileLimitedDropsUseCase,
) {
    private val log = LoggerFactory.getLogger(DropReconciliationWorker::class.java)

    @Scheduled(
        initialDelayString = "\${app.limited-drop.reconciliation.initial-delay-millis:60000}",
        fixedDelayString = "\${app.limited-drop.reconciliation.fixed-delay-millis:60000}",
    )
    fun reconcile() {
        log.debug("DropReconciliationWorker: starting reconciliation")
        try {
            reconcileLimitedDropsUseCase.execute()
        } catch (exception: DataAccessException) {
            log.warn("DropReconciliationWorker: 인프라 접근 실패로 이번 주기 대사를 건너뜁니다", exception)
        } catch (exception: RedisLockException) {
            log.warn("DropReconciliationWorker: Redis 락 장애로 이번 주기 대사를 건너뜁니다", exception)
        }
        log.debug("DropReconciliationWorker: reconciliation completed")
    }
}
