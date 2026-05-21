package com.sportsapp.scenario.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
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
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

class LogoutScenarioTest(
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

    private fun login(email: String, password: String): LoginResponse {
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        return objectMapper.readValue(response.body, LoginResponse::class.java)
    }

    init {
        Given("로그인한 유저가 있을 때") {
            userDomainService.register("logout-s01@example.com", "ValidPass123")
            val loginResult = login("logout-s01@example.com", "ValidPass123")

            When("[S-01] POST /auth/logout 을 accessToken 으로 호출하면") {
                val headers = HttpHeaders().apply { setBearerAuth(loginResult.accessToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/auth/logout",
                    HttpMethod.POST,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("204 No Content 가 반환된다") {
                    response.statusCode shouldBe HttpStatus.NO_CONTENT
                }
            }

            When("[S-01] 로그아웃 후 동일 accessToken 으로 /auth/logout 을 재호출하면") {
                val logoutHeaders = HttpHeaders().apply { setBearerAuth(loginResult.accessToken) }
                restTemplate.exchange(
                    "${baseUrl()}/auth/logout",
                    HttpMethod.POST,
                    HttpEntity<Void>(logoutHeaders),
                    String::class.java,
                )

                val reLogoutHeaders = HttpHeaders().apply { setBearerAuth(loginResult.accessToken) }
                val reLogoutResponse = restTemplate.exchange(
                    "${baseUrl()}/auth/logout",
                    HttpMethod.POST,
                    HttpEntity<Void>(reLogoutHeaders),
                    String::class.java,
                )

                Then("401 응답이 반환된다 (블랙리스트된 토큰은 SecurityContext 에 등록되지 않으므로)") {
                    reLogoutResponse.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("로그인한 유저가 로그아웃한 뒤") {
            userDomainService.register("logout-s02@example.com", "ValidPass456")
            val loginResult = login("logout-s02@example.com", "ValidPass456")

            val logoutHeaders = HttpHeaders().apply { setBearerAuth(loginResult.accessToken) }
            restTemplate.exchange(
                "${baseUrl()}/auth/logout",
                HttpMethod.POST,
                HttpEntity<Void>(logoutHeaders),
                String::class.java,
            )

            When("[S-02] 동일 refreshToken 으로 POST /auth/refresh 를 호출하면") {
                val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
                val body = objectMapper.writeValueAsString(mapOf("refreshToken" to loginResult.refreshToken))
                val response = restTemplate.exchange(
                    "${baseUrl()}/auth/refresh",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
