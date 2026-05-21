package com.sportsapp.scenario.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.mcp.IssueMcpTokenResponse
import com.sportsapp.application.mcp.ListMcpTokensResponse
import com.sportsapp.domain.mcp.McpTokenStatus
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
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

class McpTokenAdminApiScenarioTest(
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

    private fun loginAndGetToken(email: String, password: String): String {
        userDomainService.register(email, password)
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

    init {
        Given("[S-01] 인증된 운영자가 유효한 scope으로 토큰 발급을 요청하면") {
            val accessToken = loginAndGetToken("mcp-issue@example.com", "Pass1234!")

            When("POST /api/admin/mcp/tokens 를 호출하면") {
                val headers = HttpHeaders().apply {
                    set("Content-Type", "application/json")
                    setBearerAuth(accessToken)
                }
                val body = objectMapper.writeValueAsString(
                    mapOf(
                        "name" to "my-mcp-token",
                        "scopes" to listOf("read:facility"),
                        "expiresAt" to null,
                    ),
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/tokens",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("[S-01] 201 응답과 평문 토큰이 1회 포함된 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    val issued = objectMapper.readValue(response.body, IssueMcpTokenResponse::class.java)
                    issued.plainToken.shouldNotBeBlank()
                    issued.status shouldBe McpTokenStatus.ACTIVE
                    issued.name shouldBe "my-mcp-token"
                }
            }
        }

        Given("[S-02] 토큰 발급 후 목록 조회 → 폐기 → 폐기 후 목록 확인 E2E 플로우") {
            val accessToken = loginAndGetToken("mcp-e2e@example.com", "Pass1234!")
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(accessToken)
            }

            // 토큰 발급
            val issueBody = objectMapper.writeValueAsString(
                mapOf(
                    "name" to "e2e-token",
                    "scopes" to listOf("read:booking"),
                    "expiresAt" to null,
                ),
            )
            val issueResponse = restTemplate.exchange(
                "${baseUrl()}/api/admin/mcp/tokens",
                HttpMethod.POST,
                HttpEntity(issueBody, headers),
                String::class.java,
            )
            val issued = objectMapper.readValue(issueResponse.body, IssueMcpTokenResponse::class.java)

            When("GET /api/admin/mcp/tokens 로 목록을 조회하면") {
                val listResponse = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/tokens",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-02] 발급한 토큰이 목록에 포함된다") {
                    listResponse.statusCode shouldBe HttpStatus.OK
                    val listResult = objectMapper.readValue(listResponse.body, ListMcpTokensResponse::class.java)
                    listResult.tokens.any { it.tokenId == issued.tokenId } shouldBe true
                }
            }

            When("DELETE /api/admin/mcp/tokens/{tokenId} 로 폐기하면") {
                val revokeResponse = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/tokens/${issued.tokenId}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-02] 204 응답이 반환된다") {
                    revokeResponse.statusCode shouldBe HttpStatus.NO_CONTENT
                }
            }

            When("폐기 후 GET /api/admin/mcp/tokens 로 목록을 다시 조회하면") {
                val listAfterRevoke = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/tokens",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-02] 폐기된 토큰이 목록에서 사라진다") {
                    listAfterRevoke.statusCode shouldBe HttpStatus.OK
                    val listResult = objectMapper.readValue(listAfterRevoke.body, ListMcpTokensResponse::class.java)
                    listResult.tokens.none { it.tokenId == issued.tokenId } shouldBe true
                }
            }
        }

        Given("[S-03] 인증 없이 MCP 토큰 API를 호출하면") {
            val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
            val body = objectMapper.writeValueAsString(
                mapOf("name" to "no-auth-token", "scopes" to listOf("read:facility"), "expiresAt" to null),
            )

            When("POST /api/admin/mcp/tokens 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/tokens",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-04] 타인의 토큰을 폐기 요청하면") {
            val ownerToken = loginAndGetToken("mcp-owner@example.com", "Pass1234!")
            val otherToken = loginAndGetToken("mcp-other@example.com", "Pass1234!")

            val ownerHeaders = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(ownerToken)
            }
            val otherHeaders = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(otherToken)
            }

            // owner가 토큰 발급
            val issueBody = objectMapper.writeValueAsString(
                mapOf("name" to "owner-token", "scopes" to listOf("read:facility"), "expiresAt" to null),
            )
            val issueResponse = restTemplate.exchange(
                "${baseUrl()}/api/admin/mcp/tokens",
                HttpMethod.POST,
                HttpEntity(issueBody, ownerHeaders),
                String::class.java,
            )
            val issued = objectMapper.readValue(issueResponse.body, IssueMcpTokenResponse::class.java)

            When("other가 DELETE /api/admin/mcp/tokens/{tokenId} 를 호출하면") {
                val revokeResponse = restTemplate.exchange(
                    "${baseUrl()}/api/admin/mcp/tokens/${issued.tokenId}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(otherHeaders),
                    String::class.java,
                )

                Then("[S-04] 4xx 응답이 반환된다") {
                    revokeResponse.statusCode.is4xxClientError shouldBe true
                }
            }
        }
    }
}
