package com.sportsapp.domain.alerting.vo

/**
 * lookback 구간(10분, FR-4) 텔레메트리 스냅샷 — Prometheus/Loki/Tempo 조회 결과를 병합한 값이다.
 * 소스별 부분 실패는 [TelemetryQueryGateway][com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway]가
 * 흡수해 해당 섹션을 빈 값으로 채운다.
 */
data class TelemetrySnapshot(
    val metricsSummary: String,
    val logSamples: List<String>,
    val traceSamples: List<String>,
)
