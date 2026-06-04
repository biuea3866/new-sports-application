package com.sportsapp.scenario.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.mcp.GetMcpUsageAnalyticsResponse
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogRepository
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class McpUsageAnalyticsScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val mcpAuditLogRepository: McpAuditLogRepository,
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

    private fun formatUtc(dateTime: ZonedDateTime): String =
        dateTime.withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

    private fun loginAndGetToken(email: String, password: String): String {
        val user = userDomainService.register(email, password)
        userDomainService.assignRole(adminId = user.id, userId = user.id, roleName = "ADMIN")
        return doLogin(email, password)
    }

    private fun loginAsUserAndGetToken(email: String, password: String): String {
        userDomainService.register(email, password)
        return doLogin(email, password)
    }

    private fun doLogin(email: String, password: String): String {
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        val json = objectMapper.readTree(response.body)
        return json.get("accessToken").asText()
    }

    private fun getUserId(email: String): Long {
        val result: Long? = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)
        return result ?: error("user not found: $email")
    }

    private fun saveAuditLog(
        userId: Long,
        tokenId: Long,
        toolName: String = "getBookings",
        statusCode: Int = 200,
        latencyMs: Int = 150,
        calledAt: ZonedDateTime = ZonedDateTime.now().minusDays(1),
    ): McpAuditLog = mcpAuditLogRepository.save(
        McpAuditLog(
            tokenId = tokenId,
            userId = userId,
            toolName = toolName,
            paramsMasked = null,
            statusCode = statusCode,
            latencyMs = latencyMs,
            clientUserAgent = null,
            ipAddr = "127.0.0.1",
            asn = null,
            calledAt = calledAt,
        )
    )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
        }

        Given("[S-01] 인증된 운영자에게 본인 MCP 사용 분석 데이터가 있을 때") {
            val accessToken = loginAndGetToken("analytics-main@example.com", "Pass1234!")
            val userId = getUserId("analytics-main@example.com")
            val now = ZonedDateTime.now()

            saveAuditLog(userId, tokenId = 1L, toolName = "getBookings", statusCode = 200, calledAt = now.minusDays(1))
            saveAuditLog(userId, tokenId = 1L, toolName = "getBookings", statusCode = 200, calledAt = now.minusDays(2))
            saveAuditLog(userId, tokenId = 1L, toolName = "createSlot", statusCode = 403, calledAt = now.minusDays(1))

            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            When("GET /api/admin/mcp/usage-analytics 를 호출하면") {
                val from = formatUtc(now.minusDays(7))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/usage-analytics?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-01] 200 응답과 일별·tool별 집계 데이터가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, GetMcpUsageAnalyticsResponse::class.java)
                    result.dailyStats.sumOf { it.callCount } shouldBe 3L
                    result.toolCallStats.isNotEmpty() shouldBe true
                    result.errorRateStat.totalCount shouldBe 3L
                    result.errorRateStat.errorCount shouldBe 1L
                    result.toolLatencyStats.isNotEmpty() shouldBe true
                }
            }
        }

        Given("[S-02] 타 운영자의 데이터는 조회되지 않아야 한다 (IDOR 차단)") {
            val ownerToken = loginAndGetToken("analytics-owner@example.com", "Pass1234!")
            loginAndGetToken("analytics-other@example.com", "Pass1234!")

            val ownerId = getUserId("analytics-owner@example.com")
            val otherId = getUserId("analytics-other@example.com")
            val now = ZonedDateTime.now()

            saveAuditLog(ownerId, tokenId = 10L, calledAt = now.minusDays(1))
            saveAuditLog(otherId, tokenId = 20L, calledAt = now.minusDays(1))

            val ownerHeaders = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(ownerToken)
            }

            When("owner가 GET /api/admin/mcp/usage-analytics 를 호출하면") {
                val from = formatUtc(now.minusDays(7))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/usage-analytics?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(ownerHeaders),
                    String::class.java,
                )

                Then("[S-02] owner의 1건만 집계되고 other의 데이터는 포함되지 않는다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, GetMcpUsageAnalyticsResponse::class.java)
                    result.errorRateStat.totalCount shouldBe 1L
                    result.tokenUsageStats.size shouldBe 1
                    result.tokenUsageStats[0].tokenId shouldBe 10L
                }
            }
        }

        Given("[S-03] 인증 없이 usage-analytics API 호출") {
            val headers = HttpHeaders().apply { set("Content-Type", "application/json") }

            When("GET /api/admin/mcp/usage-analytics 를 호출하면") {
                val now = ZonedDateTime.now()
                val from = formatUtc(now.minusDays(7))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/usage-analytics?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-04] from/to 파라미터 없이 호출 (기본값 90일 사용)") {
            val accessToken = loginAndGetToken("analytics-default@example.com", "Pass1234!")
            val userId = getUserId("analytics-default@example.com")
            val now = ZonedDateTime.now()

            saveAuditLog(userId, tokenId = 1L, calledAt = now.minusDays(30))

            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            When("GET /api/admin/mcp/usage-analytics 를 파라미터 없이 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/usage-analytics",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-04] 200 응답과 90일 기본 범위의 집계 데이터가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, GetMcpUsageAnalyticsResponse::class.java)
                    result.errorRateStat.totalCount shouldBe 1L
                    result shouldNotBe null
                }
            }
        }

        Given("[S-05] ADMIN 롤이 없는 ROLE_USER가 usage-analytics API를 호출하면") {
            val userToken = loginAsUserAndGetToken("analytics-user-only@example.com", "Pass1234!")
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(userToken)
            }

            When("GET /api/admin/mcp/usage-analytics 를 호출하면") {
                val now = ZonedDateTime.now()
                val from = formatUtc(now.minusDays(7))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/usage-analytics?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-05] 403 Forbidden 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-06] 조회 기간이 365일을 초과하면") {
            val accessToken = loginAndGetToken("analytics-longrange@example.com", "Pass1234!")
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            When("GET /api/admin/mcp/usage-analytics 를 366일 범위로 호출하면") {
                val now = ZonedDateTime.now()
                val from = formatUtc(now.minusDays(366))
                val to = formatUtc(now)
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/usage-analytics?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-06] 400 Bad Request 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }
}
