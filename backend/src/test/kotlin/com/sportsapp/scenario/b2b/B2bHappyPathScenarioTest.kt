package com.sportsapp.scenario.b2b

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.presentation.user.dto.response.LoginResponse
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

// [S-05] 통합 happy path
// 한 owner가 facility -> event -> product -> dashboard 전체 흐름을 단일 시나리오로 실행.
// 각 도메인 등록 후 dashboard 카운트가 모두 반영됨을 검증.
class B2bHappyPathScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val mongoTemplate: MongoTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
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

    private fun jsonAuthHeaders(token: String): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply { setBearerAuth(token) }

    init {
        Given("[S-05] 통합 happy path: 한 owner가 모든 B2B 도메인을 순서대로 등록") {
            val adminPassword = "HappyAdmin1!"
            val admin = userDomainService.register("happy-path-admin@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val ownerPassword = "HappyOwner1!"
            val owner = userDomainService.register("happy-path-owner@example.com", ownerPassword)
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "FACILITY_OWNER")
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "EVENT_HOST")
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "GOODS_SELLER")

            // 기존 데이터 정리 (owner 범위만)
            mongoTemplate.remove(
                Query(Criteria.where("owner_user_id").`is`(owner.id)),
                Facility::class.java,
            )
            jdbcTemplate.execute("DELETE FROM stocks WHERE product_id IN (SELECT id FROM products WHERE owner_id = ${owner.id})")
            jdbcTemplate.execute("DELETE FROM products WHERE owner_id = ${owner.id}")
            jdbcTemplate.execute("DELETE FROM seats WHERE event_id IN (SELECT id FROM events WHERE owner_id = ${owner.id})")
            jdbcTemplate.execute("DELETE FROM events WHERE owner_id = ${owner.id}")

            stringRedisTemplate.delete("b2b:b2bDashboardSummary:${owner.id}")

            val accessToken = login("happy-path-owner@example.com", ownerPassword)

            When("[S-05] Step 1: POST /api/facility-owner/facilities 로 시설을 등록하면") {
                val facilityBody = objectMapper.writeValueAsString(
                    mapOf(
                        "code" to "HAPPY-FAC-001",
                        "name" to "통합 테스트 시설",
                        "gu" to "강남구",
                        "type" to "수영장",
                        "address" to "서울시 강남구",
                        "lat" to 37.5,
                        "lng" to 127.0,
                        "parking" to true,
                        "tel" to "02-5555-0001",
                        "homePage" to "",
                        "eduYn" to false,
                        "meta" to emptyMap<String, String>(),
                    )
                )
                val facilityResponse = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.POST,
                    HttpEntity(facilityBody, jsonAuthHeaders(accessToken)),
                    String::class.java,
                )

                Then("[S-05] 200 OK와 등록된 시설 ID가 반환된다") {
                    facilityResponse.statusCode shouldBe HttpStatus.OK
                    val facilityBody2 = objectMapper.readTree(facilityResponse.body)
                    facilityBody2["id"].asText() shouldNotBe null
                    facilityBody2["name"].asText() shouldBe "통합 테스트 시설"
                }
            }

            When("[S-05] Step 2: POST /api/event-host/events 로 이벤트를 등록하면") {
                val eventBody = """
                    {
                        "title": "통합 테스트 이벤트",
                        "venue": "서울 올림픽 경기장",
                        "startsAt": "2027-06-15T18:00:00+09:00",
                        "seats": [
                            {"sectionName":"A","seatLabel":"1","price":50000},
                            {"sectionName":"A","seatLabel":"2","price":50000}
                        ]
                    }
                """.trimIndent()
                val eventResponse = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity(eventBody, jsonAuthHeaders(accessToken)),
                    String::class.java,
                )

                Then("[S-05] 201 Created와 eventId가 반환된다") {
                    eventResponse.statusCode shouldBe HttpStatus.CREATED
                    val eventJson = objectMapper.readTree(eventResponse.body)
                    (eventJson["eventId"].asLong() > 0L) shouldBe true
                    eventJson["seatCount"].asInt() shouldBe 2
                }
            }

            When("[S-05] Step 3: POST /api/goods-seller/products 로 상품을 등록하면") {
                val productBody = objectMapper.writeValueAsString(
                    mapOf(
                        "name" to "통합 테스트 상품",
                        "category" to "EQUIPMENT",
                        "price" to 30000,
                        "description" to "통합 테스트용 상품",
                        "imageUrl" to "https://example.com/happy-product.jpg",
                    )
                )
                val productResponse = restTemplate.exchange(
                    "${baseUrl()}/api/goods-seller/products",
                    HttpMethod.POST,
                    HttpEntity(productBody, jsonAuthHeaders(accessToken)),
                    String::class.java,
                )

                Then("[S-05] 201 Created와 상품 정보가 반환된다") {
                    productResponse.statusCode shouldBe HttpStatus.CREATED
                    val productJson = objectMapper.readTree(productResponse.body)
                    (productJson["id"].asLong() > 0L) shouldBe true
                    productJson["name"].asText() shouldBe "통합 테스트 상품"
                    productJson["status"].asText() shouldBe "INACTIVE"
                    productJson["stockQuantity"].asInt() shouldBe 0
                }
            }

            When("[S-05] Step 4: GET /api/operator/dashboard/summary 로 전체 현황을 조회하면") {
                stringRedisTemplate.delete("b2b:b2bDashboardSummary:${owner.id}")
                val dashboardResponse = restTemplate.exchange(
                    "${baseUrl()}/api/operator/dashboard/summary",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(accessToken)),
                    String::class.java,
                )

                Then("[S-05] 200 OK와 모든 도메인 카운트가 반영된 요약이 반환된다") {
                    dashboardResponse.statusCode shouldBe HttpStatus.OK
                    val dashJson = objectMapper.readTree(dashboardResponse.body)

                    dashJson.has("facilities") shouldBe true
                    (dashJson["facilities"]["count"].asLong() >= 1L) shouldBe true

                    dashJson.has("events") shouldBe true
                    val eventsJson = dashJson["events"]
                    val totalEvents = eventsJson["scheduled"].asLong() + eventsJson["open"].asLong() + eventsJson["closed"].asLong()
                    (totalEvents >= 1L) shouldBe true

                    dashJson.has("products") shouldBe true
                }
            }

            When("[S-05] Step 5: GET /api/facility-owner/facilities 로 등록한 시설 목록을 조회하면") {
                val facilitiesResponse = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(accessToken)),
                    String::class.java,
                )

                Then("[S-05] 200 OK와 등록된 시설이 포함된 목록이 반환된다") {
                    facilitiesResponse.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(facilitiesResponse.body)
                    val hasFacility = body["content"].any { node ->
                        node["name"].asText() == "통합 테스트 시설"
                    }
                    hasFacility shouldBe true
                }
            }
        }
    }
}
