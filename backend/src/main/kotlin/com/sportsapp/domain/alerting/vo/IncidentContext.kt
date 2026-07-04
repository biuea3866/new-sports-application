package com.sportsapp.domain.alerting.vo

/**
 * [IncidentAnalysisGateway][com.sportsapp.domain.alerting.gateway.IncidentAnalysisGateway]에
 * LLM 분석을 요청할 때 전달하는 컨텍스트 — 신호·환경·텔레메트리 스냅샷을 묶는다.
 */
data class IncidentContext(
    val signal: AlertSignal,
    val env: String,
    val snapshot: TelemetrySnapshot,
)
