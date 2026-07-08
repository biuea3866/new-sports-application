package com.sportsapp.scenario.security

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

private const val WEBHOOK_TOKEN = "wireup-integration-secret"

/**
 * 통합 와이어업: `/internal/alerts` 하위 전체 경로가 Spring Security 필터 체인을 permitAll로
 * 통과해 컨트롤러 자체 시크릿 검증까지 도달하는지 검증한다(TDD.md 참고: Release Scenario, 실패경로).
 * 기존 도메인 API 인가 규칙(관리자 전용 경로 등)은 [B2BSecurityScenarioTest]가 회귀를 담당한다.
 */
@TestPropertySource(properties = ["alerting.webhook-token=$WEBHOOK_TOKEN"])
class AlertWebhookSecurityScenarioTest(
    @LocalServerPort private val port: Int,
) : BaseIntegrationTest() {

    private val restTemplate = RestTemplate(
        HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()),
    ).apply {
        errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }

    private fun baseUrl() = "http://localhost:$port"

    init {
        Given("유효한 Authorization: Bearer 시크릿을 담은 Grafana webhook 요청") {
            When("인증 없이(익명) POST /internal/alerts/grafana를 호출하면") {
                val headers = HttpHeaders().apply {
                    set("Authorization", "Bearer $WEBHOOK_TOKEN")
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                }
                val body = """
                    {"alerts":[{"labels":{"alertname":"HighLatency","endpoint":"/pay","source":"latency","severity":"warn","env":"prod"},"annotations":{"summary":"P95 초과"}}]}
                """.trimIndent()
                val response = restTemplate.exchange(
                    "${baseUrl()}/internal/alerts/grafana",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("Spring Security 필터를 permitAll로 통과해 컨트롤러가 202를 반환한다") {
                    response.statusCode shouldBe HttpStatus.ACCEPTED
                }
            }
        }

        Given("Authorization 헤더 없이 보낸 Grafana webhook 요청") {
            When("인증 없이 POST /internal/alerts/grafana를 호출하면") {
                val headers = HttpHeaders().apply {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                }
                val body = """
                    {"alerts":[{"labels":{"alertname":"HighLatency","endpoint":"/pay","source":"latency","severity":"warn","env":"prod"},"annotations":{"summary":"P95 초과"}}]}
                """.trimIndent()
                val response = restTemplate.exchange(
                    "${baseUrl()}/internal/alerts/grafana",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("permitAll을 통과해 컨트롤러 자체 검증이 401을 반환한다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("올바른 X-Alert-Token을 담은 내부 raise 요청") {
            When("인증 없이 POST /internal/alerts를 호출하면") {
                val headers = HttpHeaders().apply {
                    set("X-Alert-Token", WEBHOOK_TOKEN)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                }
                val body = """{"endpoint":"/orders","source":"deployment","severity":"critical","env":"prod"}"""
                val response = restTemplate.exchange(
                    "${baseUrl()}/internal/alerts",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("Spring Security 필터를 permitAll로 통과해 컨트롤러가 202를 반환한다") {
                    response.statusCode shouldBe HttpStatus.ACCEPTED
                }
            }
        }
    }
}
