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
) {
    /**
     * 모든 섹션이 비어 있는지 — 메트릭 요약이 공백이고 로그·trace 샘플이 0건이면 true.
     * [com.sportsapp.domain.alerting.entity.Alert.buildDeliveryBody]가 "원지표 없음"을 판정하는 기준이다.
     * 정규 [empty]뿐 아니라 공백만 담긴 비정규 빈 스냅샷도 동일하게 없음으로 판정한다.
     */
    val isEmpty: Boolean
        get() = metricsSummary.isBlank() && logSamples.isEmpty() && traceSamples.isEmpty()

    companion object {
        /** 원지표를 전혀 조회하지 못했을 때(모든 소스 실패)의 빈 스냅샷. */
        fun empty(): TelemetrySnapshot = TelemetrySnapshot(
            metricsSummary = "",
            logSamples = emptyList(),
            traceSamples = emptyList(),
        )
    }
}
