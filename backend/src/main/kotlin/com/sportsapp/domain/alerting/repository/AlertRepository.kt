package com.sportsapp.domain.alerting.repository

import com.sportsapp.domain.alerting.entity.Alert

/**
 * [Alert] 영속화 계약(FR-9, 이력) — 구현체는 infrastructure의 `AlertRepositoryImpl`(+ JPA)이 담당한다.
 */
interface AlertRepository {
    fun save(alert: Alert): Alert
    fun findById(alertId: Long): Alert?
}
