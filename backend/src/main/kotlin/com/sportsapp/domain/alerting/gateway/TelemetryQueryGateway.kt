package com.sportsapp.domain.alerting.gateway

import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import java.time.Duration

/**
 * Prometheus/Loki/Tempo에서 lookback 구간 텔레메트리를 조회하는 domain gateway.
 * 구현체는 infrastructure의 `TelemetryQueryGatewayImpl`이 담당한다.
 */
interface TelemetryQueryGateway {

    /**
     * [signal] 기준으로 [lookback] 구간의 텔레메트리를 조회한다.
     * 소스별 부분 실패는 구현체가 흡수한다 — 해당 섹션만 빈 값으로 채우고 전체 예외를 던지지 않는다.
     */
    fun queryContext(signal: AlertSignal, lookback: Duration): TelemetrySnapshot
}
