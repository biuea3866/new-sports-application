package com.sportsapp.infrastructure.alerting.gateway

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Prometheus(PromQL)·Loki(LogQL)·Tempo(TraceQL)를 각각 조회해 [lookback] 구간(10분, FR-4)
 * 텔레메트리 스냅샷으로 병합합니다. 소스별로 독립된 try/catch를 두어 한 소스가 실패해도
 * 나머지 섹션은 채워지고 전체 예외를 던지지 않습니다(TDD.md §실패경로·동시성·멱등).
 */
@Component
class TelemetryQueryGatewayImpl(
    restClientFactory: ExternalRestClientFactory,
    private val properties: TelemetryProperties,
) : TelemetryQueryGateway {

    private val prometheusClient: RestClient = restClientFactory.create(properties.prometheusBaseUrl)
    private val lokiClient: RestClient = restClientFactory.create(properties.lokiBaseUrl)
    private val tempoClient: RestClient = restClientFactory.create(properties.tempoBaseUrl)
    private val logger = LoggerFactory.getLogger(TelemetryQueryGatewayImpl::class.java)

    override fun queryContext(signal: AlertSignal, lookback: Duration): TelemetrySnapshot =
        TelemetrySnapshot(
            metricsSummary = queryMetricsSummary(signal, lookback),
            logSamples = queryLogSamples(signal, lookback),
            traceSamples = queryTraceSamples(signal, lookback),
        )

    private fun queryMetricsSummary(signal: AlertSignal, lookback: Duration): String = try {
        val p95Millis = queryScalar(p95Query(signal.endpoint, lookback)) * MILLIS_PER_SECOND
        val errorRatePercent = queryScalar(errorRateQuery(signal.endpoint, lookback)) * PERCENT_SCALE
        "P95=%.0fms, 에러율=%.1f%%".format(p95Millis, errorRatePercent)
    } catch (exception: RestClientException) {
        logger.warn("telemetry metrics query failed (endpoint={}): {}", signal.endpoint, exception.message)
        ""
    }

    // 값에 PromQL/LogQL/TraceQL 특유의 `{}`·`"` 문자가 포함되므로 UriBuilder 콜백(.queryParam) 대신
    // 문자열 템플릿 오버로드(.uri(template, uriVariables))를 사용한다 — 이 경로만 템플릿 변수 값을
    // 안전하게 percent-encode 한 뒤 치환한다. 콜백 스타일은 `{`/`}`를 템플릿 플레이스홀더로 오인해
    // URISyntaxException을 던진다.
    private fun queryScalar(promQl: String): Double {
        val response = prometheusClient.get()
            .uri("$PROMETHEUS_QUERY_PATH?query={query}", promQl)
            .retrieve()
            .body(PrometheusInstantQueryResponse::class.java)
        return response?.firstScalarValue() ?: 0.0
    }

    private fun queryLogSamples(signal: AlertSignal, lookback: Duration): List<String> = try {
        val now = ZonedDateTime.now()
        val response = lokiClient.get()
            .uri(
                "$LOKI_QUERY_RANGE_PATH?query={query}&start={start}&end={end}&limit={limit}",
                logQl(signal.endpoint),
                now.minus(lookback).toEpochNano(),
                now.toEpochNano(),
                SAMPLE_LIMIT,
            )
            .retrieve()
            .body(LokiQueryRangeResponse::class.java)
        response?.logLines(SAMPLE_LIMIT) ?: emptyList()
    } catch (exception: RestClientException) {
        logger.warn("telemetry loki query failed (endpoint={}): {}", signal.endpoint, exception.message)
        emptyList()
    }

    private fun queryTraceSamples(signal: AlertSignal, lookback: Duration): List<String> = try {
        val now = ZonedDateTime.now()
        val response = tempoClient.get()
            .uri(
                "$TEMPO_SEARCH_PATH?q={q}&start={start}&end={end}&limit={limit}",
                traceQl(signal.endpoint),
                now.minus(lookback).toEpochSecond(),
                now.toEpochSecond(),
                SAMPLE_LIMIT,
            )
            .retrieve()
            .body(TempoSearchResponse::class.java)
        response?.toSampleLines(SAMPLE_LIMIT) ?: emptyList()
    } catch (exception: RestClientException) {
        logger.warn("telemetry tempo query failed (endpoint={}): {}", signal.endpoint, exception.message)
        emptyList()
    }

    private fun p95Query(endpoint: String, lookback: Duration): String =
        "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri=\"$endpoint\"}" +
            "[${lookback.seconds}s])) by (le))"

    private fun errorRateQuery(endpoint: String, lookback: Duration): String =
        "sum(rate(http_server_requests_seconds_count{uri=\"$endpoint\",outcome=\"SERVER_ERROR\"}[${lookback.seconds}s])) " +
            "/ sum(rate(http_server_requests_seconds_count{uri=\"$endpoint\"}[${lookback.seconds}s]))"

    private fun logQl(endpoint: String): String =
        "{service_name=\"$LOKI_SERVICE_LABEL\"} |= \"$endpoint\" |= \"ERROR\""

    private fun traceQl(endpoint: String): String =
        "{ span.http.route = \"$endpoint\" && duration > ${SLOW_TRACE_THRESHOLD_MS}ms }"

    private fun ZonedDateTime.toEpochNano(): Long {
        val instant = this.toInstant()
        return instant.epochSecond * NANOS_PER_SECOND + instant.nano
    }

    companion object {
        private const val PROMETHEUS_QUERY_PATH = "/api/v1/query"
        private const val LOKI_QUERY_RANGE_PATH = "/loki/api/v1/query_range"
        private const val TEMPO_SEARCH_PATH = "/api/search"
        private const val LOKI_SERVICE_LABEL = "sports-app-backend"
        private const val SAMPLE_LIMIT = 5
        private const val SLOW_TRACE_THRESHOLD_MS = 500
        private const val MILLIS_PER_SECOND = 1000.0
        private const val PERCENT_SCALE = 100.0
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrometheusInstantQueryResponse(
    val status: String = "",
    val data: PrometheusQueryData? = null,
) {
    fun firstScalarValue(): Double =
        data?.result?.firstOrNull()?.value?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrometheusQueryData(
    val resultType: String = "",
    val result: List<PrometheusVectorResult> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrometheusVectorResult(
    val metric: Map<String, String> = emptyMap(),
    val value: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LokiQueryRangeResponse(
    val status: String = "",
    val data: LokiQueryData? = null,
) {
    fun logLines(limit: Int): List<String> =
        data?.result.orEmpty()
            .flatMap { stream -> stream.values.mapNotNull { it.getOrNull(1) } }
            .take(limit)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LokiQueryData(
    val resultType: String = "",
    val result: List<LokiStreamResult> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LokiStreamResult(
    val stream: Map<String, String> = emptyMap(),
    val values: List<List<String>> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TempoSearchResponse(
    val traces: List<TempoTraceSummary> = emptyList(),
) {
    fun toSampleLines(limit: Int): List<String> =
        traces.take(limit).map { "traceId=${it.traceID}, durationMs=${it.durationMs}, name=${it.rootTraceName}" }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TempoTraceSummary(
    val traceID: String = "",
    val rootServiceName: String = "",
    val rootTraceName: String = "",
    val durationMs: Long = 0,
)
