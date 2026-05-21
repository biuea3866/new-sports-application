package com.sportsapp.scenario.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.mcp.IssueMcpTokenResponse
import com.sportsapp.domain.user.UserDomainService
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
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

// McpTokenAuthenticationFilter 시나리오 테스트.
// /mcp/{path} 경로는 McpTokenAuthenticationFilter를 거치며, 유효한 MCP 토큰이 없으면 401.
// 유효한 토큰으로 접근 시 endpoint가 없어 404가 반환되지만 이는 인증 통과를 의미한다.
class McpTokenAuthFilterScenarioTest(
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

    private fun issueMcpToken(jwtToken: String, name: String): IssueMcpTokenResponse {
        val headers = HttpHeaders().apply {
            set("Content-Type", "application/json")
            setBearerAuth(jwtToken)
        }
        val body = objectMapper.writeValueAsString(
            mapOf("name" to name, "scopes" to listOf("read:facility"), "expiresAt" to null),
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
        Given("[S-01] 발급된 MCP 토큰으로 /mcp/** 경로를 요청하면") {
            val jwtToken = loginAndGetJwtToken("mcp-filter-s01@example.com", "Pass1234!")
            val issued = issueMcpToken(jwtToken, "filter-test-token")

            When("Bearer <plainToken> 으로 GET /mcp/tools 를 요청하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth(issued.plainToken)
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/tools",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-01] 401이 아닌 응답을 받는다 — 인증은 통과하고 endpoint 없음") {
                    response.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-02] 잘못된 MCP 토큰으로 /mcp/** 경로를 요청하면") {
            When("존재하지 않는 Bearer 토큰으로 GET /mcp/tools 를 요청하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth("mcp_9999999_fakesecret")
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/tools",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-02] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-03] 폐기된 MCP 토큰으로 /mcp/** 경로를 요청하면") {
            val jwtToken = loginAndGetJwtToken("mcp-filter-s03@example.com", "Pass1234!")
            val issued = issueMcpToken(jwtToken, "revoke-test-token")

            // 토큰 폐기
            val revokeHeaders = HttpHeaders().apply {
                set("Content-Type", "application/json")
                setBearerAuth(jwtToken)
            }
            restTemplate.exchange(
                "${baseUrl()}/api/admin/mcp/tokens/${issued.tokenId}",
                HttpMethod.DELETE,
                HttpEntity<Void>(revokeHeaders),
                String::class.java,
            )

            When("폐기된 토큰으로 GET /mcp/tools 를 요청하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth(issued.plainToken)
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/mcp/tools",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
