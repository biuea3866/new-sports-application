package com.sportsapp.infrastructure.alerting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sportsapp.domain.alerting.exception.IncidentAnalysisException
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.IncidentContext
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import com.sportsapp.infrastructure.alerting.gateway.ClaudeClient
import com.sportsapp.infrastructure.alerting.gateway.IncidentAnalysisGatewayImpl
import com.sportsapp.infrastructure.alerting.gateway.LlmProperties
import com.sportsapp.infrastructure.external.ExternalContractSupport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

private val objectMapper: ObjectMapper = jacksonObjectMapper()

private fun jsonResponse(body: String): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(body)

private fun gatewayFor(mockWebServer: MockWebServer, readTimeoutSeconds: Long = 5L): IncidentAnalysisGatewayImpl {
    val properties = LlmProperties(
        baseUrl = mockWebServer.url("/").toString(),
        apiKey = "test-api-key",
        model = "claude-fable-5",
        readTimeoutSeconds = readTimeoutSeconds,
        maxTokens = 512,
    )
    val claudeClient = ClaudeClient(properties)
    return IncidentAnalysisGatewayImpl(claudeClient, objectMapper)
}

private fun claudeMessagesResponse(text: String): String {
    val escapedText = objectMapper.writeValueAsString(text)
    return """
        {
          "id": "msg_01",
          "type": "message",
          "role": "assistant",
          "content": [
            { "type": "text", "text": $escapedText }
          ]
        }
    """.trimIndent()
}

private fun incidentContext(
    endpoint: String = "/api/v1/bookings",
    source: AlertSource = AlertSource.LATENCY,
    severity: AlertSeverity = AlertSeverity.CRITICAL,
    env: String = "production",
    metricsSummary: String = "p95=2300ms (threshold 500ms)",
    logSamples: List<String> = listOf("ERROR BookingTimeoutException at BookingService.kt:42"),
    traceSamples: List<String> = listOf("trace=abcd span=db-query duration=1800ms"),
): IncidentContext = IncidentContext(
    signal = AlertSignal(endpoint = endpoint, source = source, severity = severity),
    env = env,
    snapshot = TelemetrySnapshot(
        metricsSummary = metricsSummary,
        logSamples = logSamples,
        traceSamples = traceSamples,
    ),
)

/**
 * IncidentAnalysisGatewayImpl 계약 테스트 (BE-06, ADR-002).
 * mock Claude Messages API 서버로 정상 파싱·타임아웃·5xx·파싱 실패·프롬프트 구성을 검증한다.
 */
class IncidentAnalysisGatewayImplTest : BehaviorSpec({

    Given("Claude API 가 정상 응답을 반환하면") {
        When("analyze 를 호출하면") {
            Then("errorType/causeEstimation/remediation 을 파싱하고 included=true 를 반환한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val analysisJson = """
                    {"errorType":"DB_TIMEOUT","causeEstimation":"커넥션 풀 고갈로 쿼리 지연","remediation":"커넥션 풀 크기 증설"}
                """.trimIndent()
                mockWebServer.enqueue(jsonResponse(claudeMessagesResponse(analysisJson)))
                val gateway = gatewayFor(mockWebServer)

                val result = gateway.analyze(incidentContext())

                result.errorType shouldBe "DB_TIMEOUT"
                result.causeEstimation shouldBe "커넥션 풀 고갈로 쿼리 지연"
                result.remediation shouldBe "커넥션 풀 크기 증설"
                result.included shouldBe true

                mockWebServer.shutdown()
            }
        }
    }

    Given("Claude API 가 read timeout 내에 응답하지 않으면") {
        When("analyze 를 호출하면") {
            Then("IncidentAnalysisException 을 던진다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val gateway = gatewayFor(mockWebServer, readTimeoutSeconds = 1L)

                shouldThrow<IncidentAnalysisException> {
                    gateway.analyze(incidentContext())
                }

                mockWebServer.shutdown()
            }
        }
    }

    Given("Claude API 가 5xx 를 반환하면") {
        When("analyze 를 호출하면") {
            Then("IncidentAnalysisException 을 던진다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
                val gateway = gatewayFor(mockWebServer)

                shouldThrow<IncidentAnalysisException> {
                    gateway.analyze(incidentContext())
                }

                mockWebServer.shutdown()
            }
        }
    }

    Given("Claude 응답 텍스트가 예상 밖 포맷이면") {
        When("analyze 를 호출하면") {
            Then("IncidentAnalysisException 을 던진다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(jsonResponse(claudeMessagesResponse("이건 JSON 형식이 아닌 자유 텍스트 응답입니다")))
                val gateway = gatewayFor(mockWebServer)

                shouldThrow<IncidentAnalysisException> {
                    gateway.analyze(incidentContext())
                }

                mockWebServer.shutdown()
            }
        }
    }

    Given("IncidentContext 로 analyze 를 호출하면") {
        When("Claude API 로 전송하는 프롬프트를 캡처하면") {
            Then("signal(endpoint/source/severity)과 텔레메트리 요약이 프롬프트에 포함된다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val analysisJson = """
                    {"errorType":"DB_TIMEOUT","causeEstimation":"원인","remediation":"조치"}
                """.trimIndent()
                mockWebServer.enqueue(jsonResponse(claudeMessagesResponse(analysisJson)))
                val gateway = gatewayFor(mockWebServer)

                gateway.analyze(
                    incidentContext(
                        endpoint = "/api/v1/orders",
                        source = AlertSource.OVERSELL,
                        severity = AlertSeverity.WARN,
                        metricsSummary = "oversell_count=3",
                        logSamples = listOf("WARN StockShortageException"),
                        traceSamples = listOf("trace=xyz span=order-create duration=900ms"),
                    ),
                )

                val recordedRequest = mockWebServer.takeRequest()
                val requestBody = objectMapper.readTree(recordedRequest.body.readUtf8())
                val promptContent = requestBody.get("messages")[0].get("content").asText()

                promptContent shouldContain "/api/v1/orders"
                promptContent shouldContain "OVERSELL"
                promptContent shouldContain "WARN"
                promptContent shouldContain "oversell_count=3"
                promptContent shouldContain "StockShortageException"
                promptContent shouldContain "order-create"

                mockWebServer.shutdown()
            }
        }
    }
})
