package com.sportsapp.domain.alerting.repository

import com.sportsapp.domain.alerting.vo.AlertSignal
import java.time.Duration

/**
 * 신호 단위 쿨다운(FR-7, ADR-003) 원자적 획득 계약 — 구현체는 infrastructure의
 * `AlertCooldownRepositoryImpl`(Redis `SET NX PX`)이 담당한다. 키 계약은 INFRA-01 참조.
 *
 * [env]는 호출부(`AlertDomainService.raise`)가 이 알림을 발생시킨 [com.sportsapp.domain.alerting.dto.RaiseAlertCommand.env]를
 * 그대로 전달한다 — Alert 이력(signalKey/env)을 만드는 것과 동일한 값이어야 쿨다운 판정과 이력이 항상 일치한다
 * (후속 수정: 과거에는 구현체가 배포 인스턴스 자신의 `app.env`를 별도로 주입받아 두 출처가 갈릴 수 있었다).
 */
interface AlertCooldownRepository {

    /** [signal]·[env]의 쿨다운 키를 [cooldown] TTL로 원자 획득한다. true=획득(발송 진행), false=쿨다운 중(억제). */
    fun tryAcquire(signal: AlertSignal, env: String, cooldown: Duration): Boolean
}
