package com.sportsapp.scenario.b2b

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
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

// [S-02] Role 매트릭스 권한 거부 검증
// - EVENT_HOST: POST /api/facility-owner/facilities -> 403
// - GOODS_SELLER: POST /api/event-host/events -> 403
// - FACILITY_OWNER: POST /api/goods-seller/products -> 403
// - EVENT_HOST + GOODS_SELLER 다중 Role: 자기 도메인 API -> 200
class B2bPermissionMatrixScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) : BaseIntegrationTest() {

    companion object {
        private const val CREATE_EVENT_BODY =
            """{"title":"권한 테스트 이벤트","venue":"테스트 장소","startsAt":"2027-01-01T18:00:00+09:00","seats":[{"sectionName":"A","seatLabel":"1","price":10000}]}"""
    }

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

    private fun jsonAuthHeaders(token: String): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

    private fun registerFacilityBody(): String =
        objectMapper.writeValueAsString(
            mapOf(
                "code" to "PERM-TEST-001",
                "name" to "권한 테스트 시설",
                "gu" to "강남구",
                "type" to "수영장",
                "address" to "서울시 강남구",
                "lat" to 37.5,
                "lng" to 127.0,
                "parking" to true,
                "tel" to "02-0000-0000",
                "homePage" to "",
                "eduYn" to false,
                "meta" to emptyMap<String, String>(),
            )
        )

    private fun createProductBody(): String =
        objectMapper.writeValueAsString(
            mapOf(
                "name" to "권한 테스트 상품",
                "category" to "EQUIPMENT",
                "price" to 10000,
                "description" to "권한 매트릭스 테스트",
                "imageUrl" to "https://example.com/test.jpg",
            )
        )

    init {
        Given("[S-02] EVENT_HOST 권한만 가진 사용자가") {
            val adminPassword = "PermAdmin1!"
            val admin = userDomainService.register("perm-admin@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val eventHostPassword = "EventHost1!"
            val eventHost = userDomainService.register("perm-event-host@example.com", eventHostPassword)
            userDomainService.assignRole(adminId = admin.id, userId = eventHost.id, roleName = "EVENT_HOST")
            val eventHostToken = login("perm-event-host@example.com", eventHostPassword)

            When("[S-02] POST /api/facility-owner/facilities 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.POST,
                    HttpEntity(registerFacilityBody(), jsonAuthHeaders(eventHostToken)),
                    String::class.java,
                )

                Then("[S-02] 403 Forbidden이 반환된다 (facility:write 권한 없음)") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-02] GOODS_SELLER 권한만 가진 사용자가") {
            val adminPassword = "PermAdmin2!"
            val admin = userDomainService.register("perm-admin-2@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val goodsSellerPassword = "GoodsSeller1!"
            val goodsSeller = userDomainService.register("perm-goods-seller@example.com", goodsSellerPassword)
            userDomainService.assignRole(adminId = admin.id, userId = goodsSeller.id, roleName = "GOODS_SELLER")
            val goodsSellerToken = login("perm-goods-seller@example.com", goodsSellerPassword)

            When("[S-02] POST /api/event-host/events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity(CREATE_EVENT_BODY, jsonAuthHeaders(goodsSellerToken)),
                    String::class.java,
                )

                Then("[S-02] 403 Forbidden이 반환된다 (event:write 권한 없음)") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-02] FACILITY_OWNER 권한만 가진 사용자가") {
            val adminPassword = "PermAdmin3!"
            val admin = userDomainService.register("perm-admin-3@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val facilityOwnerPassword = "FacilityOwner1!"
            val facilityOwner = userDomainService.register("perm-facility-owner@example.com", facilityOwnerPassword)
            userDomainService.assignRole(adminId = admin.id, userId = facilityOwner.id, roleName = "FACILITY_OWNER")
            val facilityOwnerToken = login("perm-facility-owner@example.com", facilityOwnerPassword)

            When("[S-02] POST /api/goods-seller/products 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/goods-seller/products",
                    HttpMethod.POST,
                    HttpEntity(createProductBody(), jsonAuthHeaders(facilityOwnerToken)),
                    String::class.java,
                )

                Then("[S-02] 403 Forbidden이 반환된다 (product:write 권한 없음)") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-02] EVENT_HOST + GOODS_SELLER 다중 Role 사용자가") {
            val adminPassword = "PermAdmin4!"
            val admin = userDomainService.register("perm-admin-4@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val multiRolePassword = "MultiRole1!"
            val multiRoleUser = userDomainService.register("perm-multi-role@example.com", multiRolePassword)
            userDomainService.assignRole(adminId = admin.id, userId = multiRoleUser.id, roleName = "EVENT_HOST")
            userDomainService.assignRole(adminId = admin.id, userId = multiRoleUser.id, roleName = "GOODS_SELLER")
            val multiRoleToken = login("perm-multi-role@example.com", multiRolePassword)

            When("[S-02] POST /api/event-host/events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity(CREATE_EVENT_BODY, jsonAuthHeaders(multiRoleToken)),
                    String::class.java,
                )

                Then("[S-02] 201 Created가 반환된다 (EVENT_HOST 권한 보유)") {
                    response.statusCode shouldBe HttpStatus.CREATED
                }
            }

            When("[S-02] POST /api/goods-seller/products 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/goods-seller/products",
                    HttpMethod.POST,
                    HttpEntity(createProductBody(), jsonAuthHeaders(multiRoleToken)),
                    String::class.java,
                )

                Then("[S-02] 201 Created가 반환된다 (GOODS_SELLER 권한 보유)") {
                    response.statusCode shouldBe HttpStatus.CREATED
                }
            }
        }
    }
}
