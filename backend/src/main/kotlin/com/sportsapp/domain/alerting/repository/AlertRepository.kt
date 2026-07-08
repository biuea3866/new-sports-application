package com.sportsapp.domain.alerting.repository

import com.sportsapp.domain.alerting.entity.Alert
import java.time.ZonedDateTime

/**
 * [Alert] 영속화 계약(FR-9, 이력) — 구현체는 infrastructure의 `AlertRepositoryImpl`(+ JPA)이 담당한다.
 */
interface AlertRepository {
    fun save(alert: Alert): Alert
    fun findById(alertId: Long): Alert?

    /**
     * 보존 정책(기본 90일, mcp_audit_logs 선례) — [cutoff] 이전에 발생(raised_at)한 이력을
     * 하드 삭제하고 삭제된 행 수를 반환한다. 이력 정리 목적이므로 소프트 삭제가 아니다.
     */
    fun deleteRaisedBefore(cutoff: ZonedDateTime): Long
}
