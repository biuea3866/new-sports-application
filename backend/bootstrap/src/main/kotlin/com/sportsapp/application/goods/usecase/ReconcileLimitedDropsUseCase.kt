package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.LimitedDropDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 활성 회차(SCHEDULED|OPEN) 대사(reconciliation) 트랜잭션 경계 (BE-11, Observability).
 * [DropReconciliationWorker]가 스케줄 주기마다 호출한다.
 */
@Service
class ReconcileLimitedDropsUseCase(
    private val limitedDropDomainService: LimitedDropDomainService,
) {
    @Transactional(readOnly = true)
    fun execute() {
        limitedDropDomainService.reconcileAllActive()
    }
}
