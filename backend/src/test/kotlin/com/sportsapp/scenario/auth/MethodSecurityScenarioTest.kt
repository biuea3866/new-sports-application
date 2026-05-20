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

class MethodSecurityScenarioTest(
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
        check(response.statusCode == HttpStatus.OK) { "Login failed for $email: ${response.statusCode}" }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    init {
        Given("ADMIN 과 일반 USER 가 등록된 상태") {
            val adminPassword = "AdminPass111"
            val admin = userDomainService.register("method-sec-admin@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val userPassword = "UserPass222"
            val normalUser = userDomainService.register("method-sec-user@example.com", userPassword)

            When("[S-01] ADMIN 이 /admin/users/{userId}/roles/{roleName} 을 호출하면") {
                val adminToken = login("method-sec-admin@example.com", adminPassword)
                val headers = HttpHeaders().apply { setBearerAuth(adminToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/${normalUser.id}/roles/FACILITY_OWNER",
                    HttpMethod.POST,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("200 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                }
            }

            When("[S-02] 일반 USER 가 /admin/users/{userId}/roles/{roleName} 을 호출하면") {
                val userToken = login("method-sec-user@example.com", userPassword)
                val headers = HttpHeaders().apply { setBearerAuth(userToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/999/roles/FACILITY_OWNER",
                    HttpMethod.POST,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-02] 403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }

            When("[S-03] 인증 없이 @PreAuthorize 가 붙은 /admin/users 엔드포인트를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/999/roles/FACILITY_OWNER",
                    HttpMethod.POST,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
