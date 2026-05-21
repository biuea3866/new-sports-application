package com.sportsapp.scenario.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.mcp.ListMcpAuditLogsResponse
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogRepository
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
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

class McpAuditLogAdminApiScenarioTest(
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

    private fun saveAuditLog(userId: Long, tokenId: Long, calledAt: ZonedDateTime): McpAuditLog =
        mcpAuditLogRepository.save(
            McpAuditLog(
                tokenId = tokenId,
                userId = userId,
                toolName = "read:facility",
                paramsMasked = null,
                statusCode = 200,
                latencyMs = 50,
                clientUserAgent = null,
                ipAddr = "127.0.0.1",
                asn = null,
                calledAt = calledAt,
            ),
        )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
        }

        Given("[S-01] 인증된 운영자가 본인 audit log 조회를 요청하면") {
            val accessToken = loginAndGetToken("audit-main@example.com", "Pass1234!")
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            // userId를 얻기 위해 토큰 목록 조회 후 userId 추출 (간접 확인)
            val userInfoResponse = restTemplate.exchange(
                "${baseUrl()}/api/admin/mcp/tokens",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )
            val userId = objectMapper.readTree(userInfoResponse.body)
                .path("tokens").firstOrNull()?.path("userId")?.asLong()
                ?: run {
                    // 토큰 없으면 별도 사용자 DB 조회 방식으로 진행
                    // 로그인한 사용자의 userId를 DB에서 직접 조회
                    jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE email = ?",
                        Long::class.java,
                        "audit-main@example.com",
                    )
                }

            val now = ZonedDateTime.now()
            saveAuditLog(userId, 10L, now.minusHours(1))
            saveAuditLog(userId, 10L, now.minusHours(2))

            When("GET /api/admin/mcp/audit-logs 를 기간 파라미터로 호출하면") {
                val from = formatUtc(now.minusDays(1))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/audit-logs?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-01] 200 응답과 본인 audit log 2건이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, ListMcpAuditLogsResponse::class.java)
                    result.totalElements shouldBe 2
                    result.content.size shouldBe 2
                }
            }
        }

        Given("[S-02] 타인의 audit log는 조회되지 않아야 한다") {
            val ownerToken = loginAndGetToken("audit-owner@example.com", "Pass1234!")
            loginAndGetToken("audit-other@example.com", "Pass1234!")

            val ownerHeaders = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(ownerToken)
            }

            val ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long::class.java,
                "audit-owner@example.com",
            )
            val otherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long::class.java,
                "audit-other@example.com",
            )

            val now = ZonedDateTime.now()
            saveAuditLog(ownerId, 20L, now.minusMinutes(30))
            saveAuditLog(otherId, 30L, now.minusMinutes(15))

            When("owner가 GET /api/admin/mcp/audit-logs 를 호출하면") {
                val from = formatUtc(now.minusDays(1))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/audit-logs?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(ownerHeaders),
                    String::class.java,
                )

                Then("[S-02] owner의 1건만 반환되고 other의 로그는 포함되지 않는다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, ListMcpAuditLogsResponse::class.java)
                    result.totalElements shouldBe 1
                    result.content.all { it.tokenId == 20L } shouldBe true
                }
            }
        }

        Given("[S-03] 해당 기간에 audit log가 없으면") {
            val accessToken = loginAndGetToken("audit-empty@example.com", "Pass1234!")
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            When("GET /api/admin/mcp/audit-logs 를 빈 기간으로 호출하면") {
                val now = ZonedDateTime.now()
                val from = formatUtc(now.minusDays(1))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/audit-logs?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-03] 200 응답과 빈 페이지가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, ListMcpAuditLogsResponse::class.java)
                    result.totalElements shouldBe 0
                    result.content.isEmpty() shouldBe true
                }
            }
        }

        Given("[S-04] 인증 없이 audit log API를 호출하면") {
            val headers = HttpHeaders().apply { set("Content-Type", "application/json") }

            When("GET /api/admin/mcp/audit-logs 를 호출하면") {
                val now = ZonedDateTime.now()
                val from = formatUtc(now.minusDays(1))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/audit-logs?from=$from&to=$to",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-04] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-05] 페이징 파라미터(page=0, size=1)로 조회하면") {
            val accessToken = loginAndGetToken("audit-paging@example.com", "Pass1234!")
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            val userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long::class.java,
                "audit-paging@example.com",
            )

            val now = ZonedDateTime.now()
            saveAuditLog(userId, 40L, now.minusHours(3))
            saveAuditLog(userId, 40L, now.minusHours(2))
            saveAuditLog(userId, 40L, now.minusHours(1))

            When("page=0, size=1 로 GET /api/admin/mcp/audit-logs 를 호출하면") {
                val from = formatUtc(now.minusDays(1))
                val to = formatUtc(now.plusMinutes(1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/audit-logs?from=$from&to=$to&page=0&size=1",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-05] totalElements=3 이고 content는 1건만 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val result = objectMapper.readValue(response.body, ListMcpAuditLogsResponse::class.java)
                    result.totalElements shouldBe 3
                    result.content.size shouldBe 1
                    result.pageSize shouldBe 1
                    result.totalPages shouldBe 3
                }
            }
        }
    }
}
