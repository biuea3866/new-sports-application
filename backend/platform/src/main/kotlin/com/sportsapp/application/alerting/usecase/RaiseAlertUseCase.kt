package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.service.AlertDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내부 raise(③오버셀·⑧배포실패) Command → raise 위임 (TDD.md §Detail Design, `POST /internal/alerts`).
 * source/severity는 presentation의 Request→Command 변환 단계에서 이미 enum으로 파싱돼 들어온다.
 */
@Service
class RaiseAlertUseCase(
    private val alertDomainService: AlertDomainService,
) {
    @Transactional
    fun execute(command: RaiseAlertCommand): Alert? =
        alertDomainService.raise(command)
}
