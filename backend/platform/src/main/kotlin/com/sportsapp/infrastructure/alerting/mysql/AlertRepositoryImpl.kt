package com.sportsapp.infrastructure.alerting.mysql

import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.repository.AlertCustomRepository
import com.sportsapp.domain.alerting.repository.AlertRepository
import java.time.ZonedDateTime
import org.springframework.stereotype.Repository

/** [AlertRepository]의 MySQL 구현체 — 알림 이력 영속화(FR-9). */
@Repository
class AlertRepositoryImpl(
    private val alertJpaRepository: AlertJpaRepository,
    private val alertCustomRepository: AlertCustomRepository,
) : AlertRepository {

    override fun save(alert: Alert): Alert = alertJpaRepository.save(alert)

    override fun findById(alertId: Long): Alert? = alertJpaRepository.findByIdAndDeletedAtIsNull(alertId)

    override fun deleteRaisedBefore(cutoff: ZonedDateTime): Long = alertCustomRepository.deleteRaisedBefore(cutoff)
}
