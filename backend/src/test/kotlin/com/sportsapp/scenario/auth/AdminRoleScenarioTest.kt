package com.sportsapp.scenario.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.presentation.user.dto.response.LoginResponse
import com.sportsapp.domain.user.service.UserDomainService
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

class AdminRoleScenarioTest(
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
        Given("ADMIN Role을 가진 관리자와 일반 USER가 등록된 상태") {
            val adminPassword = "AdminPass123"
            val admin = userDomainService.register("admin-role-test@example.com", adminPassword)
            userDomainService.assignRole(
                adminId = admin.id,
                userId = admin.id,
                roleName = "ADMIN",
            )

            val userPassword = "UserPass456"
            val normalUser = userDomainService.register("normal-role-test@example.com", userPassword)

            When("[S-01] ADMIN이 일반 사용자에게 FACILITY_OWNER Role을 부여하면") {
                val adminToken = login("admin-role-test@example.com", adminPassword)
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

                Then("해당 사용자의 Role 목록에 FACILITY_OWNER가 포함된다") {
                    val roles = userDomainService.getRolesForUser(normalUser.id)
                    roles.any { it.name == "FACILITY_OWNER" } shouldBe true
                }
            }
        }

        Given("USER Role만 가진 일반 사용자가 등록된 상태") {
            val userPassword = "UserOnly789"
            userDomainService.register("user-only-test@example.com", userPassword)

            When("[S-02] 일반 사용자가 Role 관리 API를 호출하면") {
                val userToken = login("user-only-test@example.com", userPassword)
                val headers = HttpHeaders().apply { setBearerAuth(userToken) }
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

        Given("ADMIN이 자기 자신의 ADMIN Role을 회수하려는 상태") {
            val adminPassword = "SelfRevokePass"
            val admin = userDomainService.register("self-revoke-test@example.com", adminPassword)
            userDomainService.assignRole(
                adminId = admin.id,
                userId = admin.id,
                roleName = "ADMIN",
            )

            When("[S-03] ADMIN이 자기 자신의 ADMIN Role을 회수 시도하면") {
                val adminToken = login("self-revoke-test@example.com", adminPassword)
                val headers = HttpHeaders().apply { setBearerAuth(adminToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/${admin.id}/roles/ADMIN",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("409 ProblemDetail 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CONFLICT
                }
            }
        }
    }
}
