package com.sportsapp.scenario.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

class LoginScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val passwordEncoder: PasswordEncoder,
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

    init {
        Given("등록된 유저가 있을 때") {
            val hashedPassword = passwordEncoder.encode("ValidPass123")
            userDomainService.register("login-scenario@example.com", hashedPassword)

            When("[S-01] POST /auth/login 에 올바른 자격증명을 보내면") {
                val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
                val body = objectMapper.writeValueAsString(
                    mapOf("email" to "login-scenario@example.com", "password" to "ValidPass123"),
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/auth/login",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("200 응답과 accessToken/refreshToken 이 포함된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val loginResponse = objectMapper.readValue(response.body, LoginResponse::class.java)
                    loginResponse.accessToken.shouldNotBeBlank()
                    loginResponse.refreshToken.shouldNotBeBlank()
                }
            }

            When("[S-02] POST /auth/login 에 잘못된 비밀번호를 보내면") {
                val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
                val body = objectMapper.writeValueAsString(
                    mapOf("email" to "login-scenario@example.com", "password" to "WrongPassword"),
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/auth/login",
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    String::class.java,
                )

                Then("401 ProblemDetail 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("유효한 accessToken 으로 보호 API 를 호출할 때") {
            val hashedPassword = passwordEncoder.encode("SecurePass456")
            userDomainService.register("protected-scenario@example.com", hashedPassword)

            val loginHeaders = HttpHeaders().apply { set("Content-Type", "application/json") }
            val loginBody = objectMapper.writeValueAsString(
                mapOf("email" to "protected-scenario@example.com", "password" to "SecurePass456"),
            )
            val loginResponse = restTemplate.exchange(
                "${baseUrl()}/auth/login",
                HttpMethod.POST,
                HttpEntity(loginBody, loginHeaders),
                String::class.java,
            )
            val loginResult = objectMapper.readValue(loginResponse.body, LoginResponse::class.java)

            When("[S-03] Authorization: Bearer <accessToken> 헤더로 /actuator/health 를 호출하면") {
                val headers = HttpHeaders().apply { setBearerAuth(loginResult.accessToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/actuator/health",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("200 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                }
            }
        }

        Given("만료된 Refresh Token 으로 갱신 시도 시") {
            When("[S-04] POST /auth/refresh 에 존재하지 않는 refreshToken 을 보내면") {
                val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
                val body = objectMapper.writeValueAsString(
                    mapOf("userId" to 9999, "refreshToken" to "expired-or-nonexistent-token"),
                )
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
