package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.service.AlertDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 1시간 주기 self-check heartbeat 트리거 → selfCheck 위임
 * (TDD.md §Detail Design `AlertSelfCheckScheduler`).
 */
@Service
class SendSelfCheckUseCase(
    private val alertDomainService: AlertDomainService,
) {
    @Transactional
    fun execute() {
        alertDomainService.selfCheck()
    }
}
