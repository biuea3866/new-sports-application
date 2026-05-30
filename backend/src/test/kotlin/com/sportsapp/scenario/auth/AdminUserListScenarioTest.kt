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

class AdminUserListScenarioTest(
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
        Given("[S-01] ADMIN 사용자가 등록된 상태에서 회원 목록을 조회하면") {
            val adminPassword = "AdminList123"
            val admin = userDomainService.register("admin-list-scenario@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            userDomainService.register("target-list-scenario@example.com", "Pass123")

            When("[S-01] ADMIN 토큰으로 GET /admin/users 를 호출하면") {
                val adminToken = login("admin-list-scenario@example.com", adminPassword)
                val headers = HttpHeaders().apply { setBearerAuth(adminToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users?page=0&size=10",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-01] 200 응답과 함께 사용자 목록이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                }

                Then("[S-01] 응답 body 에 content 배열이 포함된다") {
                    val body = objectMapper.readTree(response.body)
                    body.has("content") shouldBe true
                    body.has("totalElements") shouldBe true
                }
            }
        }

        Given("[S-02] 일반 USER 사용자가 등록된 상태에서 회원 목록을 조회하면") {
            val userPassword = "UserList456"
            userDomainService.register("user-list-scenario@example.com", userPassword)

            When("[S-02] USER 토큰으로 GET /admin/users 를 호출하면") {
                val userToken = login("user-list-scenario@example.com", userPassword)
                val headers = HttpHeaders().apply { setBearerAuth(userToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users?page=0&size=10",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-02] 403 Forbidden 이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-03] ADMIN 이 email 부분검색 필터로 목록을 조회하면") {
            val adminPassword = "AdminSearch789"
            val admin = userDomainService.register("admin-search-scenario@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            userDomainService.register("search-target-scenario@example.com", "Pass123")
            userDomainService.register("other-scenario-user@example.com", "Pass456")

            When("[S-03] emailKeyword=search-target 으로 GET /admin/users 를 호출하면") {
                val adminToken = login("admin-search-scenario@example.com", adminPassword)
                val headers = HttpHeaders().apply { setBearerAuth(adminToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users?page=0&size=10&emailKeyword=search-target",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-03] 200 응답과 함께 해당 email 이 포함된 사용자만 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    val content = body.get("content")
                    (0 until content.size()).all { index ->
                        content[index].get("email").asText().contains("search-target")
                    } shouldBe true
                }
            }
        }
    }
}
