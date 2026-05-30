package com.sportsapp.scenario.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.mcp.IssueMcpTokenResponse
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

/**
 * BE-12 MCP Server bootstrap + Read tool wiring 시나리오 테스트.
 * - Spring AI MCP SSE transport 가 /mcp/sse 에서 동작하는지 검증
 * - MCP 토큰 인증 통과 후 tool 호출 시 정상 응답 반환
 */
class McpServerBootstrapScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
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

    private fun loginAndGetJwtToken(email: String, password: String): String {
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

    private fun issueMcpToken(jwtToken: String, name: String, scopes: List<String>): IssueMcpTokenResponse {
        val headers = HttpHeaders().apply {
            set("Content-Type", "application/json")
            setBearerAuth(jwtToken)
        }
        val body = objectMapper.writeValueAsString(
            mapOf("name" to name, "scopes" to scopes, "expiresAt" to null),
        )
        val response = restTemplate.exchange(
            "${baseUrl()}/api/admin/mcp/tokens",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        return objectMapper.readValue(response.body, IssueMcpTokenResponse::class.java)
    }

    init {
        Given("[S-01] MCP SSE endpoint가 설정된 경로에 노출되어야 한다") {
            When("GET /mcp/sse 를 인증 없이 요청하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/sse",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("[S-01] 401 또는 MCP SSE 응답이 반환된다 — endpoint 존재 확인") {
                    // 인증 없이 요청하면 401이 반환된다 (endpoint는 존재하고 /mcp/** 인증 범위에 포함됨)
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-02] MCP 토큰으로 인증한 후 /mcp/message 에 tools/list 요청을 보내면") {
            val jwtToken = loginAndGetJwtToken("mcp-bootstrap-s02@example.com", "Pass1234!")
            val issued = issueMcpToken(jwtToken, "bootstrap-test-token", listOf("read:facility"))

            When("MCP 초기화 + tools/list 요청을 /mcp/message 에 전송하면") {
                val headers = HttpHeaders().apply {
                    set("Content-Type", "application/json")
                    setBearerAuth(issued.plainToken)
                }

                // MCP JSON-RPC 초기화 요청
                val initBody = objectMapper.writeValueAsString(
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to 1,
                        "method" to "initialize",
                        "params" to mapOf(
                            "protocolVersion" to "2024-11-05",
                            "capabilities" to emptyMap<String, Any>(),
                            "clientInfo" to mapOf("name" to "test-client", "version" to "1.0"),
                        ),
                    ),
                )
                val initResponse = restTemplate.exchange(
                    "${baseUrl()}/mcp/message",
                    HttpMethod.POST,
                    HttpEntity(initBody, headers),
                    String::class.java,
                )

                Then("[S-02] 401이 아닌 응답이 반환된다 — 인증 통과 확인") {
                    initResponse.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-03] 유효한 MCP 토큰으로 tools/list 를 요청하면") {
            val jwtToken = loginAndGetJwtToken("mcp-bootstrap-s03@example.com", "Pass1234!")
            val issued = issueMcpToken(jwtToken, "tools-list-token", listOf("read:facility"))

            When("SSE 세션 없이 /mcp/message 에 tools/list JSON-RPC 요청을 전송하면") {
                val headers = HttpHeaders().apply {
                    set("Content-Type", "application/json")
                    setBearerAuth(issued.plainToken)
                }
                val toolsListBody = objectMapper.writeValueAsString(
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to 2,
                        "method" to "tools/list",
                        "params" to emptyMap<String, Any>(),
                    ),
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/message",
                    HttpMethod.POST,
                    HttpEntity(toolsListBody, headers),
                    String::class.java,
                )

                Then("[S-03] 401이 아닌 응답이 반환된다 — MCP 토큰 인증이 통과한다") {
                    response.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-04] read:facility scope 를 가진 MCP 토큰으로 getFacilities tool 을 호출하면") {
            val jwtToken = loginAndGetJwtToken("mcp-bootstrap-s04@example.com", "Pass1234!")
            val issued = issueMcpToken(jwtToken, "facility-scope-token", listOf("read:facility"))

            When("/mcp/message 에 tools/call getFacilities JSON-RPC 요청을 전송하면") {
                val headers = HttpHeaders().apply {
                    set("Content-Type", "application/json")
                    setBearerAuth(issued.plainToken)
                }
                val toolCallBody = objectMapper.writeValueAsString(
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to 4,
                        "method" to "tools/call",
                        "params" to mapOf(
                            "name" to "getFacilities",
                            "arguments" to mapOf("gu" to null, "type" to null, "page" to 0, "size" to 10),
                        ),
                    ),
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/message",
                    HttpMethod.POST,
                    HttpEntity(toolCallBody, headers),
                    String::class.java,
                )

                Then("[S-04] 401이 아닌 응답이 반환된다 — read:facility scope 보유 시 인가가 통과한다") {
                    response.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-05] read:facility scope 없는 MCP 토큰 (read:booking only) 으로 getFacilities tool 을 호출하면") {
            val jwtToken = loginAndGetJwtToken("mcp-bootstrap-s05@example.com", "Pass1234!")
            // read:booking scope 만 부여 — getFacilities 에 필요한 read:facility scope 없음
            val issued = issueMcpToken(jwtToken, "booking-only-scope-token", listOf("read:booking"))

            When("/mcp/message 에 tools/call getFacilities JSON-RPC 요청을 전송하면") {
                val headers = HttpHeaders().apply {
                    set("Content-Type", "application/json")
                    setBearerAuth(issued.plainToken)
                }
                val toolCallBody = objectMapper.writeValueAsString(
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to 5,
                        "method" to "tools/call",
                        "params" to mapOf(
                            "name" to "getFacilities",
                            "arguments" to mapOf("gu" to null, "type" to null, "page" to 0, "size" to 10),
                        ),
                    ),
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/message",
                    HttpMethod.POST,
                    HttpEntity(toolCallBody, headers),
                    String::class.java,
                )

                Then("[S-05] 인증은 통과하지만 tool 실행이 거부된다 — 응답에 OK 결과가 없다") {
                    // 인증 자체는 통과 (read:booking scope 보유 토큰은 유효한 MCP 토큰)
                    response.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                    val body = response.body
                    body shouldNotBe null
                    // @PreAuthorize 거부 시 Spring AI MCP 는 JSON-RPC error 로 래핑하거나
                    // HTTP 403 을 반환한다. 두 경우 모두 정상 tool 결과(OK status)는 없어야 한다.
                    val responseBody = requireNotNull(body)
                    val isHttpForbidden = response.statusCode == HttpStatus.FORBIDDEN
                    val hasNoOkResult = !responseBody.contains("\"status\":\"OK\"")
                    (isHttpForbidden || hasNoOkResult) shouldBe true
                }
            }
        }
    }
}
