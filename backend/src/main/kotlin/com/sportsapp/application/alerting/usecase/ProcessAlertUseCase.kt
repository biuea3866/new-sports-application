package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.service.AlertDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * `AlertProcessingRequestedEvent` 소비(AFTER_COMMIT @Async) → process 위임
 * (TDD.md §Detail Design `AlertProcessingEventWorker`).
 */
@Service
class ProcessAlertUseCase(
    private val alertDomainService: AlertDomainService,
) {
    @Transactional
    fun execute(alertId: Long) {
        alertDomainService.process(alertId)
    }
}
