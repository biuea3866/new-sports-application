package com.sportsapp.scenario.facility

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

class B2bFacilityScenarioTest(
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
        check(response.statusCode == HttpStatus.OK) { "Login failed: ${response.body}" }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun registerOwner(email: String, password: String): Long {
        val user = userDomainService.register(email, password)
        userDomainService.assignRole(adminId = user.id, userId = user.id, roleName = "FACILITY_OWNER")
        return user.id
    }

    private fun facilityBody(code: String): String = objectMapper.writeValueAsString(
        mapOf(
            "code" to code,
            "name" to "B2B 테스트 시설 $code",
            "gu" to "강남구",
            "type" to "수영장",
            "address" to "서울시 강남구 테스트로 1",
            "lat" to 37.5,
            "lng" to 127.0,
            "parking" to true,
            "tel" to "02-0000-0000",
            "homePage" to "",
            "eduYn" to false,
            "meta" to emptyMap<String, String>(),
        ),
    )

    private fun authHeaders(token: String): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(token)
        set("Content-Type", "application/json")
    }

    private fun extractFacilityId(locationHeader: String): String = locationHeader.substringAfterLast("/")

    init {
        Given("[S-01] FACILITY_OWNER 사용자 — 시설 등록 → 목록 조회 → 수정 → 삭제 전체 흐름") {
            registerOwner("b2b-owner-s01@example.com", "OwnerTest123!")
            val token = login("b2b-owner-s01@example.com", "OwnerTest123!")
            val headers = authHeaders(token)

            When("POST /api/b2b/facilities 로 시설 등록") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/facilities",
                    HttpMethod.POST,
                    HttpEntity(facilityBody("B2B-S01-001"), headers),
                    String::class.java,
                )

                Then("[S-01] 201 응답과 Location 헤더가 반환되고 목록/수정/삭제 흐름이 성공한다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    val location = requireNotNull(response.headers.location?.toString()) { "Location header must be present" }
                    location shouldContain "/api/b2b/facilities/"
                    val facilityId = extractFacilityId(location)

                    val listResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/facilities",
                        HttpMethod.GET,
                        HttpEntity<Void>(headers),
                        String::class.java,
                    )
                    listResponse.statusCode shouldBe HttpStatus.OK
                    listResponse.body shouldContain "B2B-S01-001"

                    val patchBody = objectMapper.writeValueAsString(mapOf("fee" to "5000"))
                    val patchResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/facilities/$facilityId",
                        HttpMethod.PATCH,
                        HttpEntity(patchBody, headers),
                        String::class.java,
                    )
                    patchResponse.statusCode shouldBe HttpStatus.OK

                    val deleteResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/facilities/$facilityId",
                        HttpMethod.DELETE,
                        HttpEntity<Void>(headers),
                        String::class.java,
                    )
                    deleteResponse.statusCode shouldBe HttpStatus.NO_CONTENT
                }
            }
        }

        Given("[S-02] 다른 사용자가 동일 시설 PATCH 시 404") {
            registerOwner("b2b-owner-s02-a@example.com", "OwnerTest123!")
            registerOwner("b2b-owner-s02-b@example.com", "OwnerTest123!")

            val token1 = login("b2b-owner-s02-a@example.com", "OwnerTest123!")
            val headers1 = authHeaders(token1)
            val registerResponse = restTemplate.exchange(
                "${baseUrl()}/api/b2b/facilities",
                HttpMethod.POST,
                HttpEntity(facilityBody("B2B-S02-001"), headers1),
                String::class.java,
            )
            val facilityId = extractFacilityId(
                requireNotNull(registerResponse.headers.location?.toString()) { "Location header required" },
            )

            When("owner2가 owner1의 시설에 PATCH 요청 시") {
                val token2 = login("b2b-owner-s02-b@example.com", "OwnerTest123!")
                val headers2 = authHeaders(token2)
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/facilities/$facilityId",
                    HttpMethod.PATCH,
                    HttpEntity(objectMapper.writeValueAsString(mapOf("fee" to "9999")), headers2),
                    String::class.java,
                )

                Then("[S-02] 404 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("[S-03] Role 미보유 사용자가 POST 호출 시 403") {
            userDomainService.register("b2b-noauth-s03@example.com", "UserTest123!")

            When("FACILITY_OWNER role 없이 POST /api/b2b/facilities 호출 시") {
                val token = login("b2b-noauth-s03@example.com", "UserTest123!")
                val headers = authHeaders(token)
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/facilities",
                    HttpMethod.POST,
                    HttpEntity(facilityBody("B2B-S03-001"), headers),
                    String::class.java,
                )

                Then("[S-03] 403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-04] soft-delete 후 목록 조회에서 미포함") {
            registerOwner("b2b-owner-s04@example.com", "OwnerTest123!")
            val token = login("b2b-owner-s04@example.com", "OwnerTest123!")
            val headers = authHeaders(token)

            val registerResponse = restTemplate.exchange(
                "${baseUrl()}/api/b2b/facilities",
                HttpMethod.POST,
                HttpEntity(facilityBody("B2B-S04-001"), headers),
                String::class.java,
            )
            val facilityId = extractFacilityId(
                requireNotNull(registerResponse.headers.location?.toString()) { "Location header required" },
            )

            restTemplate.exchange(
                "${baseUrl()}/api/b2b/facilities/$facilityId",
                HttpMethod.DELETE,
                HttpEntity<Void>(headers),
                String::class.java,
            )

            When("soft-delete 후 GET /api/b2b/facilities 호출 시") {
                val listResponse = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    String::class.java,
                )

                Then("[S-04] 삭제된 시설이 목록에 포함되지 않는다") {
                    listResponse.statusCode shouldBe HttpStatus.OK
                    val node = objectMapper.readTree(listResponse.body)
                    val content = node.get("content")
                    val ids = (0 until content.size()).map { content.get(it).get("id").asText() }
                    ids.contains(facilityId) shouldBe false
                }
            }
        }
    }
}
