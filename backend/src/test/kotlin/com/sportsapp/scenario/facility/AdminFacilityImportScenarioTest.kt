package com.sportsapp.scenario.facility

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityRepository
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.presentation.facility.ImportLegacyFacilitiesRequest
import com.sportsapp.presentation.facility.LegacyRowRequest
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

class AdminFacilityImportScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
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
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        check(response.statusCode == HttpStatus.OK) {
            "Login failed: ${response.statusCode} for $email"
        }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun buildLegacyRowRequest(legacyId: String) = LegacyRowRequest(
        legacyId = legacyId,
        name = "시설 $legacyId",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        ycode = "37.5",
        xcode = "127.0",
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        extraFields = emptyMap(),
    )

    init {
        Given("ADMIN 계정이 등록된 상태에서 5건의 레거시 행을 apply 모드로 전송하면") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val adminPassword = "AdminImport123"
            val admin = userDomainService.register("admin-import-test@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")
            val adminToken = login("admin-import-test@example.com", adminPassword)

            val rows = (1..5).map { buildLegacyRowRequest("LEGACY-$it") }
            val request = ImportLegacyFacilitiesRequest(rows = rows, dryRun = false)

            When("[S-01] POST /admin/facilities/import 를 호출하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth(adminToken)
                    contentType = MediaType.APPLICATION_JSON
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/facilities/import",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("200 OK와 insertedCount=5가 반환되고 DB에 5건이 적재된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readValue(response.body, Map::class.java)
                    (body["insertedCount"] as Int) shouldBe 5
                    (body["dryRun"] as Boolean) shouldBe false
                    mongoTemplate.count(Query(), Facility::class.java) shouldBe 5
                }
            }
        }

        Given("ADMIN 계정이 등록된 상태에서 3건을 dryRun=true로 전송하면") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val adminPassword = "AdminDryRun456"
            val admin = userDomainService.register("admin-dryrun-test@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")
            val adminToken = login("admin-dryrun-test@example.com", adminPassword)

            val rows = (1..3).map { buildLegacyRowRequest("DRY-$it") }
            val request = ImportLegacyFacilitiesRequest(rows = rows, dryRun = true)

            When("[R-02] POST /admin/facilities/import (dryRun=true) 를 호출하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth(adminToken)
                    contentType = MediaType.APPLICATION_JSON
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/facilities/import",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("dryRun=true가 반환되고 DB에는 0건이 적재된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readValue(response.body, Map::class.java)
                    (body["dryRun"] as Boolean) shouldBe true
                    mongoTemplate.count(Query(), Facility::class.java) shouldBe 0
                }
            }
        }

        Given("ADMIN 계정이 등록된 상태에서 동일한 레거시 데이터를 두 번 임포트하면") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val adminPassword = "AdminIdempotent789"
            val admin = userDomainService.register("admin-idempotent-test@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")
            val adminToken = login("admin-idempotent-test@example.com", adminPassword)

            val rows = listOf(buildLegacyRowRequest("IDEM-001"))
            val request = ImportLegacyFacilitiesRequest(rows = rows, dryRun = false)
            val headers = HttpHeaders().apply {
                setBearerAuth(adminToken)
                contentType = MediaType.APPLICATION_JSON
            }

            When("[R-03] 동일 요청을 두 번 호출하면") {
                restTemplate.exchange(
                    "${baseUrl()}/admin/facilities/import",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )
                restTemplate.exchange(
                    "${baseUrl()}/admin/facilities/import",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("DB에는 1건만 존재한다 (upsert 멱등성)") {
                    mongoTemplate.count(Query(), Facility::class.java) shouldBe 1
                }
            }
        }

        Given("USER 권한만 가진 일반 사용자가 import를 시도하면") {
            val userPassword = "UserOnly999"
            userDomainService.register("user-no-admin-test@example.com", userPassword)
            val userToken = login("user-no-admin-test@example.com", userPassword)

            val request = ImportLegacyFacilitiesRequest(
                rows = listOf(buildLegacyRowRequest("UNAUTH-001")),
                dryRun = false,
            )

            When("POST /admin/facilities/import 를 호출하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth(userToken)
                    contentType = MediaType.APPLICATION_JSON
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/facilities/import",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("403 Forbidden이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("ADMIN 계정이 등록된 상태에서 중복 legacyId를 포함한 rows를 전송하면") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val adminPassword = "AdminDedup321"
            val admin = userDomainService.register("admin-dedup-test@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")
            val adminToken = login("admin-dedup-test@example.com", adminPassword)

            val rows = listOf(
                buildLegacyRowRequest("DEDUP-001"),
                buildLegacyRowRequest("DEDUP-001"),
            )
            val request = ImportLegacyFacilitiesRequest(rows = rows, dryRun = false)

            When("[S-01] 동일 legacyId 중복 2건을 임포트하면") {
                val headers = HttpHeaders().apply {
                    setBearerAuth(adminToken)
                    contentType = MediaType.APPLICATION_JSON
                }
                restTemplate.exchange(
                    "${baseUrl()}/admin/facilities/import",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("단일 도큐먼트로 dedupe 적재된다") {
                    mongoTemplate.count(Query(), Facility::class.java) shouldBe 1
                }
            }
        }
    }
}
