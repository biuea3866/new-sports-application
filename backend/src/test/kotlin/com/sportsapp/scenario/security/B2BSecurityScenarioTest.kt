package com.sportsapp.scenario.security

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

class B2BSecurityScenarioTest(
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

    private fun login(email: String, password: String): String {
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        check(response.statusCode == HttpStatus.OK) {
            "Login failed with status ${response.statusCode} for $email"
        }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    init {
        Given("인증되지 않은 익명 사용자") {
            When("[S-01] GET /api/b2b/facilities 를 인증 없이 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-02] GET /facilities 를 인증 없이 호출하면 (B2C 회귀 보호)") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("200 응답이 반환된다 (B2C permitAll 유지)") {
                    response.statusCode shouldBe HttpStatus.OK
                }
            }
        }

        Given("USER Role만 가진 인증 사용자") {
            val userPassword = "B2bUserTest1"
            userDomainService.register("b2b-user-test@example.com", userPassword)

            When("[S-04] 인증된 USER가 ADMIN 전용 /admin/** API를 호출하면") {
                val accessToken = login("b2b-user-test@example.com", userPassword)
                val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/999/roles/FACILITY_OWNER",
                    HttpMethod.POST,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("403 응답이 반환된다 (ADMIN Role 없는 사용자)") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("ADMIN Role 없이 JWT 인증된 사용자") {
            val userPassword = "B2bUserTest2"
            userDomainService.register("b2b-preauth-test@example.com", userPassword)

            When("[S-03] JWT 유효 사용자가 @PreAuthorize(hasRole ADMIN) 엔드포인트를 호출하면") {
                val accessToken = login("b2b-preauth-test@example.com", userPassword)
                val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/999/roles/FACILITY_OWNER",
                    HttpMethod.POST,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }
    }
}
