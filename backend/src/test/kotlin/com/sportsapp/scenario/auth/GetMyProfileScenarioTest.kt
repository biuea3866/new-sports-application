package com.sportsapp.scenario.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.GetMyProfileResponse
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.domain.user.UserStatus
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

class GetMyProfileScenarioTest(
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
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        val loginResult = objectMapper.readValue(response.body, Map::class.java)
        return loginResult["accessToken"] as String
    }

    init {
        Given("[S-01] 인증된 사용자가 GET /users/me 를 호출할 때") {
            userDomainService.register("me-scenario@example.com", "ValidPass123")
            val accessToken = loginAndGetToken("me-scenario@example.com", "ValidPass123")

            When("Authorization: Bearer 헤더와 함께 GET /users/me 를 호출하면") {
                val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/users/me",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-01] 200 OK와 이메일/상태/가입일이 담긴 프로필이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val profile = objectMapper.readValue(response.body, GetMyProfileResponse::class.java)
                    profile.email shouldBe "me-scenario@example.com"
                    profile.status shouldBe UserStatus.ACTIVE
                    profile.createdAt shouldNotBe null
                }
            }
        }

        Given("[S-02] 미인증 사용자가 GET /users/me 를 호출할 때") {
            When("Authorization 헤더 없이 GET /users/me 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/users/me",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("[S-02] 401 Unauthorized 가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
