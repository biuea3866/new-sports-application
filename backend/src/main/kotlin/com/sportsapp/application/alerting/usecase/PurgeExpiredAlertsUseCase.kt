package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.service.AlertDomainService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * alerts 이력 보존 정책(기본 90일, mcp_audit_logs 선례) 정리 배치 → purgeExpiredAlerts 위임
 * (TDD.md §Detail Design `AlertRetentionScheduler`). presentation의 일 1회 스케줄러가 호출한다.
 */
@Service
class PurgeExpiredAlertsUseCase(
    private val alertDomainService: AlertDomainService,
    @Value("\${alerting.retention.days:90}") private val retentionDays: Long,
) {
    @Transactional
    fun execute(): Long = alertDomainService.purgeExpiredAlerts(retentionDays)
}
