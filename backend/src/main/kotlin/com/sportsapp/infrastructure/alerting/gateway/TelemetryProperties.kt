package com.sportsapp.infrastructure.alerting.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Prometheus/Loki/Tempo 접속 정보 (BE-05).
 * `alerting.telemetry.*` 프로퍼티 바인딩은 BE-10이 application.yml에 정의한다.
 * 기본값은 옵저버빌리티 스택(observability/README.md, ⑤)의 로컬 compose 포트와 동일하다.
 */
@ConfigurationProperties(prefix = "alerting.telemetry")
data class TelemetryProperties(
    val prometheusBaseUrl: String = "http://localhost:9090",
    val lokiBaseUrl: String = "http://localhost:3100",
    val tempoBaseUrl: String = "http://localhost:3200",
)
