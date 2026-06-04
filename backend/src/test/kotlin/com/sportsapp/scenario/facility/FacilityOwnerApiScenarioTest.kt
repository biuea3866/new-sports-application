package com.sportsapp.scenario.facility

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.presentation.user.dto.response.LoginResponse
import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityRepository
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.facility.RegisterFacilityRequest
import com.sportsapp.presentation.facility.UpdateFacilityRequest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
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

class FacilityOwnerApiScenarioTest(
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
        check(response.statusCode == HttpStatus.OK) { "Login failed: ${response.statusCode}" }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun buildRegisterRequest(code: String) = RegisterFacilityRequest(
        code = code,
        name = "시설 $code",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
    )

    init {
        Given("FACILITY_OWNER 권한을 가진 사용자가 존재할 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val ownerPassword = "OwnerTest1"
            val adminPassword = "AdminTest1"
            val admin = userDomainService.register("b2b-admin-facility@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")
            val owner = userDomainService.register("b2b-owner-facility@example.com", ownerPassword)
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "FACILITY_OWNER")

            val ownerToken = login("b2b-owner-facility@example.com", ownerPassword)

            When("[S-01] POST /api/facility-owner/facilities 로 시설을 등록하면") {
                val request = buildRegisterRequest("B2B-GN-001")
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                    setBearerAuth(ownerToken)
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("200 OK와 등록된 시설 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    body["id"].asText().shouldNotBeBlank()
                    body["name"].asText() shouldBe "시설 B2B-GN-001"
                }
            }

            When("[S-02] GET /api/facility-owner/facilities 로 내 시설 목록을 조회하면") {
                val savedFacility = facilityRepository.save(
                    Facility.create(buildRegisterRequest("B2B-LIST-001").run {
                        FacilityAttributes(
                            code = code, name = name, gu = gu, type = type,
                            address = address, lat = lat, lng = lng,
                            parking = parking, tel = tel, homePage = homePage,
                            eduYn = eduYn, meta = meta,
                        )
                    }).also { it.assignOwner(owner.id) },
                )

                val headers = HttpHeaders().apply { setBearerAuth(ownerToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("200 OK와 내 소유 시설 목록이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    (body["totalElements"].asInt() >= 1) shouldBe true
                    requireNotNull(savedFacility.id)
                }
            }

            When("[S-03] GET /api/facility-owner/facilities/{id} 로 내 시설 단건을 조회하면") {
                val savedFacility = facilityRepository.save(
                    Facility.create(
                        FacilityAttributes(
                            code = "B2B-GET-001", name = "조회 시설", gu = "강남구", type = "수영장",
                            address = "서울시 강남구", lat = 37.5, lng = 127.0,
                            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
                            meta = emptyMap(),
                        ),
                    ).also { it.assignOwner(owner.id) },
                )
                val facilityId = requireNotNull(savedFacility.id)

                val headers = HttpHeaders().apply { setBearerAuth(ownerToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities/$facilityId",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("200 OK와 시설 단건이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    body["id"].asText() shouldBe facilityId
                }
            }

            When("[S-04] PATCH /api/facility-owner/facilities/{id} 로 내 시설 meta를 수정하면") {
                val savedFacility = facilityRepository.save(
                    Facility.create(
                        FacilityAttributes(
                            code = "B2B-UPD-001", name = "수정 시설", gu = "강남구", type = "수영장",
                            address = "서울시 강남구", lat = 37.5, lng = 127.0,
                            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
                            meta = emptyMap(),
                        ),
                    ).also { it.assignOwner(owner.id) },
                )
                val facilityId = requireNotNull(savedFacility.id)

                val updateRequest = UpdateFacilityRequest(meta = mapOf("description" to "updated"))
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                    setBearerAuth(ownerToken)
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities/$facilityId",
                    HttpMethod.PATCH,
                    HttpEntity(objectMapper.writeValueAsString(updateRequest), headers),
                    String::class.java,
                )

                Then("200 OK와 업데이트된 시설이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    body["id"].asText() shouldBe facilityId
                }
            }

            When("[S-05] 다른 사용자 소유 시설 ID로 GET /api/facility-owner/facilities/{id} 를 호출하면") {
                val otherOwner = userDomainService.register("b2b-other-owner@example.com", "Other1Pass")
                userDomainService.assignRole(adminId = admin.id, userId = otherOwner.id, roleName = "FACILITY_OWNER")

                val otherFacility = facilityRepository.save(
                    Facility.create(
                        FacilityAttributes(
                            code = "B2B-OTHER-001", name = "타인 시설", gu = "서초구", type = "헬스장",
                            address = "서울시 서초구", lat = 37.6, lng = 127.1,
                            parking = false, tel = "02-1111-1111", homePage = "", eduYn = false,
                            meta = emptyMap(),
                        ),
                    ).also { it.assignOwner(otherOwner.id) },
                )
                val otherFacilityId = requireNotNull(otherFacility.id)

                val headers = HttpHeaders().apply { setBearerAuth(ownerToken) }
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities/$otherFacilityId",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("404 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("인증되지 않은 사용자가") {
            When("[S-06] GET /api/facility-owner/facilities 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("FACILITY_OWNER 권한 없는 USER Role 사용자가") {
            val userPassword = "UserOnly1"
            userDomainService.register("b2b-user-only-facility@example.com", userPassword)
            val userToken = login("b2b-user-only-facility@example.com", userPassword)

            When("[S-07] POST /api/facility-owner/facilities 를 호출하면") {
                val request = buildRegisterRequest("B2B-FORBIDDEN-001")
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                    setBearerAuth(userToken)
                }
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.POST,
                    HttpEntity(objectMapper.writeValueAsString(request), headers),
                    String::class.java,
                )

                Then("403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }
    }
}
