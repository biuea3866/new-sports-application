package com.sportsapp.domain.alerting.vo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * lookback 구간(10분, FR-4) 텔레메트리 스냅샷 — Prometheus/Loki/Tempo 조회 결과를 병합한 값이다.
 * 소스별 부분 실패는 [TelemetryQueryGateway][com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway]가
 * 흡수해 해당 섹션을 빈 값으로 채운다.
 *
 * [isEmpty]는 계산된 프로퍼티라 Jackson이 getter `isEmpty()`를 JSON 필드 "empty"로 직렬화한다.
 * `@Type(JsonStringType::class)` 컬럼 저장 시(Hibernate의 dirty-checking deepCopy가 내부적으로
 * 직렬화→역직렬화 왕복을 수행) 이 필드가 생성자 인자가 아니라 `UnrecognizedPropertyException`으로
 * 저장 자체가 실패했다.
 *
 * 처방을 두 겹으로 둔다 — 성격이 다르다:
 * - `@get:JsonIgnore`([isEmpty]) — **원인을 좁게 차단**한다. 애초에 "empty" 를 내보내지 않아 컬럼에
 *   잉여 키가 저장되지 않고, 이후 필드 제거·개명이 조용히 무시되는 부작용도 없다.
 * - [JsonIgnoreProperties] `ignoreUnknown` — **읽기 하위 호환**. 이 수정 이전에 "empty" 키가 실려
 *   저장된 행이 남아 있을 수 있고, `JsonStringType` 의 매퍼는 미지 필드에 기본적으로 예외를 던진다.
 *   앞의 애노테이션만 두면 그 행을 읽을 수 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @get:JsonIgnore
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
