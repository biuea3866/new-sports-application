package com.sportsapp.infrastructure.alerting

import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.infrastructure.alerting.gateway.TelemetryProperties
import com.sportsapp.infrastructure.alerting.gateway.TelemetryQueryGatewayImpl
import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.Duration

private const val PROMETHEUS_SUCCESS_BODY = """
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      { "metric": {}, "value": [1719999999, "0.185"] }
    ]
  }
}
"""

private const val LOKI_SUCCESS_BODY = """
{
  "status": "success",
  "data": {
    "resultType": "streams",
    "result": [
      {
        "stream": { "level": "error" },
        "values": [ [ "1719999999000000000", "ERROR failed to process order" ] ]
      }
    ]
  }
}
"""

private const val TEMPO_SUCCESS_BODY = """
{
  "traces": [
    { "traceID": "abc123", "rootServiceName": "sports-app-backend", "rootTraceName": "GET /api/orders", "durationMs": 820 }
  ]
}
"""

private val telemetrySignal = AlertSignal(
    endpoint = "/api/orders",
    source = AlertSource.LATENCY,
    severity = AlertSeverity.CRITICAL,
)

private fun gatewayFor(
    prometheusServer: MockWebServer,
    lokiServer: MockWebServer,
    tempoServer: MockWebServer,
): TelemetryQueryGatewayImpl {
    val properties = TelemetryProperties(
        prometheusBaseUrl = prometheusServer.url("/").toString(),
        lokiBaseUrl = lokiServer.url("/").toString(),
        tempoBaseUrl = tempoServer.url("/").toString(),
    )
    return TelemetryQueryGatewayImpl(ExternalRestClientFactory(), properties)
}

/**
 * TelemetryQueryGatewayImpl 계약 테스트 (BE-05).
 * mock 서버 3종(Prometheus/Loki/Tempo)으로 병합·소스별 부분 실패·lookback 반영을 검증한다.
 */
class TelemetryQueryGatewayImplTest : BehaviorSpec({

    Given("Prometheus/Loki/Tempo 가 모두 정상 응답하면") {
        When("queryContext 를 호출하면") {
            Then("metricsSummary·logSamples·traceSamples 가 모두 채워진다") {
                val prometheusServer = ExternalContractSupport.startMockServer()
                val lokiServer = ExternalContractSupport.startMockServer()
                val tempoServer = ExternalContractSupport.startMockServer()
                prometheusServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(PROMETHEUS_SUCCESS_BODY),
                )
                prometheusServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(PROMETHEUS_SUCCESS_BODY),
                )
                lokiServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(LOKI_SUCCESS_BODY),
                )
                tempoServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(TEMPO_SUCCESS_BODY),
                )
                val gateway = gatewayFor(prometheusServer, lokiServer, tempoServer)

                val snapshot = gateway.queryContext(telemetrySignal, Duration.ofMinutes(10))

                snapshot.metricsSummary shouldContain "P95"
                snapshot.logSamples shouldHaveSize 1
                snapshot.logSamples[0] shouldContain "ERROR failed to process order"
                snapshot.traceSamples shouldHaveSize 1
                snapshot.traceSamples[0] shouldContain "abc123"

                prometheusServer.shutdown()
                lokiServer.shutdown()
                tempoServer.shutdown()
            }
        }
    }

    Given("Loki 조회가 5xx 를 반환하면") {
        When("queryContext 를 호출하면") {
            Then("예외 없이 logSamples 만 빈 값이고 metricsSummary·traceSamples 는 채워진다") {
                val prometheusServer = ExternalContractSupport.startMockServer()
                val lokiServer = ExternalContractSupport.startMockServer()
                val tempoServer = ExternalContractSupport.startMockServer()
                prometheusServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(PROMETHEUS_SUCCESS_BODY),
                )
                prometheusServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(PROMETHEUS_SUCCESS_BODY),
                )
                lokiServer.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
                tempoServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(TEMPO_SUCCESS_BODY),
                )
                val gateway = gatewayFor(prometheusServer, lokiServer, tempoServer)

                val snapshot = gateway.queryContext(telemetrySignal, Duration.ofMinutes(10))

                snapshot.logSamples.shouldBeEmpty()
                snapshot.metricsSummary shouldContain "P95"
                snapshot.traceSamples shouldHaveSize 1

                prometheusServer.shutdown()
                lokiServer.shutdown()
                tempoServer.shutdown()
            }
        }
    }

    Given("lookback 이 10분이면") {
        When("각 소스에 쿼리를 보내면") {
            Then("Prometheus 쿼리 문자열과 Loki/Tempo 시간 범위 파라미터에 600초가 반영된다") {
                val prometheusServer = ExternalContractSupport.startMockServer()
                val lokiServer = ExternalContractSupport.startMockServer()
                val tempoServer = ExternalContractSupport.startMockServer()
                prometheusServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(PROMETHEUS_SUCCESS_BODY),
                )
                prometheusServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(PROMETHEUS_SUCCESS_BODY),
                )
                lokiServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(LOKI_SUCCESS_BODY),
                )
                tempoServer.enqueue(
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(TEMPO_SUCCESS_BODY),
                )
                val gateway = gatewayFor(prometheusServer, lokiServer, tempoServer)

                gateway.queryContext(telemetrySignal, Duration.ofMinutes(10))

                val p95Request = prometheusServer.takeRequest()
                val p95Query = requireNotNull(p95Request.requestUrl?.queryParameter("query"))
                p95Query shouldContain "[600s]"

                val lokiRequest = lokiServer.takeRequest()
                val lokiStart = requireNotNull(lokiRequest.requestUrl?.queryParameter("start")?.toLong())
                val lokiEnd = requireNotNull(lokiRequest.requestUrl?.queryParameter("end")?.toLong())
                (lokiEnd - lokiStart) shouldBe Duration.ofMinutes(10).toNanos()

                val tempoRequest = tempoServer.takeRequest()
                val tempoStart = requireNotNull(tempoRequest.requestUrl?.queryParameter("start")?.toLong())
                val tempoEnd = requireNotNull(tempoRequest.requestUrl?.queryParameter("end")?.toLong())
                (tempoEnd - tempoStart) shouldBe Duration.ofMinutes(10).toSeconds()

                prometheusServer.shutdown()
                lokiServer.shutdown()
                tempoServer.shutdown()
            }
        }
    }

    Given("Prometheus/Loki/Tempo 가 모두 실패하면") {
        When("queryContext 를 호출하면") {
            Then("예외를 던지지 않고 빈 TelemetrySnapshot 을 반환한다") {
                val prometheusServer = ExternalContractSupport.startMockServer()
                val lokiServer = ExternalContractSupport.startMockServer()
                val tempoServer = ExternalContractSupport.startMockServer()
                prometheusServer.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
                lokiServer.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
                tempoServer.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
                val gateway = gatewayFor(prometheusServer, lokiServer, tempoServer)

                val snapshot = gateway.queryContext(telemetrySignal, Duration.ofMinutes(10))

                snapshot.metricsSummary shouldBe ""
                snapshot.logSamples.shouldBeEmpty()
                snapshot.traceSamples.shouldBeEmpty()

                prometheusServer.shutdown()
                lokiServer.shutdown()
                tempoServer.shutdown()
            }
        }
    }
})
