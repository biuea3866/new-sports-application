package com.sportsapp.domain.alerting.repository

import com.sportsapp.domain.alerting.vo.AlertSignal
import java.time.Duration

/**
 * 신호 단위 쿨다운(FR-7, ADR-003) 원자적 획득 계약 — 구현체는 infrastructure의
 * `AlertCooldownRepositoryImpl`(Redis `SET NX PX`)이 담당한다. 키 계약은 INFRA-01 참조.
 */
interface AlertCooldownRepository {

    /** [signal]의 쿨다운 키를 [cooldown] TTL로 원자 획득한다. true=획득(발송 진행), false=쿨다운 중(억제). */
    fun tryAcquire(signal: AlertSignal, cooldown: Duration): Boolean
}
