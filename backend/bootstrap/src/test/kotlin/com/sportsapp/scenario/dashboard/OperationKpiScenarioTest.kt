package com.sportsapp.scenario.dashboard

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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class OperationKpiScenarioTest(
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

    private fun ZonedDateTime.toUtcParam(): String =
        withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

    private fun login(email: String, password: String): String {
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        check(response.statusCode == HttpStatus.OK) { "Login failed: ${response.statusCode}" }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun getKpi(accessToken: String, from: String, to: String) = restTemplate.exchange(
        "${baseUrl()}/api/operator/dashboard/kpi?from=$from&to=$to",
        HttpMethod.GET,
        HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(accessToken) }),
        String::class.java,
    )

    init {
        Given("[S-01] FACILITY_OWNER 역할을 가진 운영자가 유효한 기간으로 KPI를 조회할 때") {
            val adminPassword = "KpiAdmin1!"
            val admin = userDomainService.register("kpi-scenario-admin@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val ownerPassword = "KpiOwner1!"
            val owner = userDomainService.register("kpi-scenario-owner@example.com", ownerPassword)
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "FACILITY_OWNER")
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "GOODS_SELLER")
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "EVENT_HOST")

            val accessToken = login("kpi-scenario-owner@example.com", ownerPassword)
            val from = ZonedDateTime.now().minusDays(7).toUtcParam()
            val to = ZonedDateTime.now().toUtcParam()

            When("GET /api/operator/dashboard/kpi 를 호출하면") {
                val response = getKpi(accessToken, from, to)

                Then("[S-01] HTTP 200과 ownerUserId를 포함한 KPI JSON이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readValue(response.body, Map::class.java)
                    (body["ownerUserId"] as Int).toLong() shouldBe owner.id
                }
            }
        }

        Given("[S-02] 인증 없이 KPI를 조회할 때") {
            When("Authorization 헤더 없이 GET /api/operator/dashboard/kpi 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/operator/dashboard/kpi",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("[S-02] HTTP 401 Unauthorized가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("[S-03] 인증된 운영자가 from > to 인 잘못된 기간으로 조회할 때") {
            val adminPassword = "KpiAdmin2!"
            val admin2 = userDomainService.register("kpi-scenario-admin2@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin2.id, userId = admin2.id, roleName = "ADMIN")

            val ownerPassword = "KpiOwner2!"
            val owner2 = userDomainService.register("kpi-scenario-owner2@example.com", ownerPassword)
            userDomainService.assignRole(adminId = admin2.id, userId = owner2.id, roleName = "FACILITY_OWNER")

            val accessToken = login("kpi-scenario-owner2@example.com", ownerPassword)
            val futureFrom = ZonedDateTime.now().plusDays(10).toUtcParam()
            val pastTo = ZonedDateTime.now().toUtcParam()

            When("from > to 파라미터로 GET /api/operator/dashboard/kpi 를 호출하면") {
                val response = getKpi(accessToken, futureFrom, pastTo)

                Then("[S-03] HTTP 400 Bad Request가 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        Given("[S-04] 인증된 운영자가 365일 초과 기간으로 조회할 때") {
            val adminPassword = "KpiAdmin3!"
            val admin3 = userDomainService.register("kpi-scenario-admin3@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin3.id, userId = admin3.id, roleName = "ADMIN")

            val ownerPassword = "KpiOwner3!"
            val owner3 = userDomainService.register("kpi-scenario-owner3@example.com", ownerPassword)
            userDomainService.assignRole(adminId = admin3.id, userId = owner3.id, roleName = "FACILITY_OWNER")

            val accessToken = login("kpi-scenario-owner3@example.com", ownerPassword)
            val from = ZonedDateTime.now().minusDays(366).toUtcParam()
            val to = ZonedDateTime.now().toUtcParam()

            When("366일 기간으로 GET /api/operator/dashboard/kpi 를 호출하면") {
                val response = getKpi(accessToken, from, to)

                Then("[S-04] HTTP 400 Bad Request가 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }
}
