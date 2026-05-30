package com.sportsapp.presentation.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.mcp.PersistAnomalyEventCommand
import com.sportsapp.application.mcp.PersistAnomalyEventUseCase
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.ZonedDateTime
import java.util.UUID

class McpAnomalyEventApiControllerTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val persistAnomalyEventUseCase: PersistAnomalyEventUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val objectMapper: ObjectMapper,
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

    private fun loginAndGetToken(email: String, password: String): String {
        userDomainService.register(email, password)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        return objectMapper.readTree(response.body).get("accessToken").asText()
    }

    private fun authHeaders(token: String): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(token)
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_anomaly_events")
        }

        Given("[S-07] 인증된 운영자가 본인 anomaly 이벤트 목록을 조회하면") {
            val token = loginAndGetToken("anomaly-list@example.com", "Pass1234!")
            val userId = objectMapper.readTree(
                restTemplate.getForObject("${baseUrl()}/api/users/me", String::class.java, HttpEntity<Void>(authHeaders(token)))
            ).run {
                // /api/users/me 가 없을 수 있으므로 JWT 디코딩 대신 DB 조회
                jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = 'anomaly-list@example.com'",
                    Long::class.java,
                )
            }

            requireNotNull(userId)
            persistAnomalyEventUseCase.execute(
                PersistAnomalyEventCommand(
                    sourceEventId = UUID.randomUUID().toString(),
                    tokenId = 1L,
                    ownerUserId = userId,
                    detectedAt = ZonedDateTime.now(),
                    currentHourCount = 200L,
                    baselineAverage = 50.0,
                )
            )

            When("GET /api/mcp/anomaly-events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/mcp/anomaly-events",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(token)),
                    String::class.java,
                )

                Then("[S-07] 200 응답과 본인 이벤트 1건이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "totalElements"
                    val json = objectMapper.readTree(response.body)
                    json.get("totalElements").asLong() shouldBe 1L
                }
            }
        }

        Given("[S-08] size=200 초과 파라미터로 GET 요청") {
            val token = loginAndGetToken("anomaly-max@example.com", "Pass1234!")

            When("size=200 으로 GET /api/mcp/anomaly-events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/mcp/anomaly-events?size=200",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(token)),
                    String::class.java,
                )

                Then("[S-08] 400 응답이 반환된다 (@Max(100) 검증 실패)") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        Given("[S-09] 인증되지 않은 요청으로 GET 호출") {
            When("Authorization 헤더 없이 GET /api/mcp/anomaly-events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/mcp/anomaly-events",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("[S-09] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-10] 본인 anomaly 이벤트를 false positive 마킹") {
            val token = loginAndGetToken("anomaly-mark@example.com", "Pass1234!")
            val userId = requireNotNull(
                jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = 'anomaly-mark@example.com'",
                    Long::class.java,
                )
            )

            val saved = requireNotNull(
                persistAnomalyEventUseCase.execute(
                    PersistAnomalyEventCommand(
                        sourceEventId = UUID.randomUUID().toString(),
                        tokenId = 2L,
                        ownerUserId = userId,
                        detectedAt = ZonedDateTime.now(),
                        currentHourCount = 300L,
                        baselineAverage = 60.0,
                    )
                )
            )

            When("POST /api/mcp/anomaly-events/{id}/false-positive 를 호출하면") {
                val body = objectMapper.writeValueAsString(mapOf("note" to "정상 배치"))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/mcp/anomaly-events/${saved.id}/false-positive",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(token)),
                    String::class.java,
                )

                Then("[S-10] 200 응답과 FALSE_POSITIVE 상태가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "FALSE_POSITIVE"
                }
            }
        }

        Given("[S-11] 타인 anomaly 이벤트를 false positive 마킹 시도") {
            loginAndGetToken("anomaly-owner@example.com", "Pass1234!")
            val attackerToken = loginAndGetToken("anomaly-attacker@example.com", "Pass1234!")
            val ownerId = requireNotNull(
                jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = 'anomaly-owner@example.com'",
                    Long::class.java,
                )
            )

            val saved = requireNotNull(
                persistAnomalyEventUseCase.execute(
                    PersistAnomalyEventCommand(
                        sourceEventId = UUID.randomUUID().toString(),
                        tokenId = 3L,
                        ownerUserId = ownerId,
                        detectedAt = ZonedDateTime.now(),
                        currentHourCount = 250L,
                        baselineAverage = 70.0,
                    )
                )
            )

            When("공격자가 POST /api/mcp/anomaly-events/{id}/false-positive 를 호출하면") {
                val body = objectMapper.writeValueAsString(mapOf("note" to null))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/mcp/anomaly-events/${saved.id}/false-positive",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(attackerToken)),
                    String::class.java,
                )

                Then("[S-11] 404 응답이 반환된다 (IDOR 차단)") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }
}
