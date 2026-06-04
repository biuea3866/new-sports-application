package com.sportsapp.scenario.b2b

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.presentation.user.dto.response.LoginResponse
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

// [S-01] Owner A, B 동시 등록 시 상호 격리
// Owner A가 facility, event, product를 등록 후
// Owner B가 자기 도메인 목록 조회 시 A의 데이터가 0건 포함되는지 검증.
class CrossDomainOwnershipIsolationScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
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

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply { setBearerAuth(token) }

    init {
        Given("[S-01] Owner A와 Owner B가 각자 리소스를 등록한 상황에서") {
            val adminPassword = "AdminOwner1!"
            val admin = userDomainService.register("isolation-admin@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val ownerAPassword = "OwnerA1!"
            val ownerA = userDomainService.register("isolation-owner-a@example.com", ownerAPassword)
            userDomainService.assignRole(adminId = admin.id, userId = ownerA.id, roleName = "FACILITY_OWNER")
            userDomainService.assignRole(adminId = admin.id, userId = ownerA.id, roleName = "EVENT_HOST")
            userDomainService.assignRole(adminId = admin.id, userId = ownerA.id, roleName = "GOODS_SELLER")

            val ownerBPassword = "OwnerB1!"
            val ownerB = userDomainService.register("isolation-owner-b@example.com", ownerBPassword)
            userDomainService.assignRole(adminId = admin.id, userId = ownerB.id, roleName = "FACILITY_OWNER")
            userDomainService.assignRole(adminId = admin.id, userId = ownerB.id, roleName = "EVENT_HOST")
            userDomainService.assignRole(adminId = admin.id, userId = ownerB.id, roleName = "GOODS_SELLER")

            // 기존 데이터 정리 (ownerA, ownerB 범위만)
            mongoTemplate.remove(
                Query(Criteria.where("owner_user_id").`in`(ownerA.id, ownerB.id)),
                Facility::class.java,
            )
            jdbcTemplate.execute("DELETE FROM stocks WHERE product_id IN (SELECT id FROM products WHERE owner_id IN (${ownerA.id}, ${ownerB.id}))")
            jdbcTemplate.execute("DELETE FROM products WHERE owner_id IN (${ownerA.id}, ${ownerB.id})")
            jdbcTemplate.execute("DELETE FROM seats WHERE event_id IN (SELECT id FROM events WHERE owner_id IN (${ownerA.id}, ${ownerB.id}))")
            jdbcTemplate.execute("DELETE FROM events WHERE owner_id IN (${ownerA.id}, ${ownerB.id})")

            // Owner A 데이터 등록
            facilityRepository.save(
                Facility.create(
                    FacilityAttributes(
                        code = "ISO-A-FAC-001",
                        name = "A 시설",
                        gu = "강남구",
                        type = "수영장",
                        address = "서울시 강남구",
                        lat = 37.5,
                        lng = 127.0,
                        parking = true,
                        tel = "02-1111-0000",
                        homePage = "",
                        eduYn = false,
                        meta = emptyMap(),
                    ),
                ).also { it.assignOwner(ownerA.id) },
            )

            val ownerAProduct = productJpaRepository.save(
                Product(
                    name = "A 상품",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("10000"),
                    description = "A owner product",
                    imageUrl = "https://example.com/a.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = ownerA.id,
                )
            )
            stockJpaRepository.save(Stock(productId = ownerAProduct.id, quantity = 5))

            eventJpaRepository.save(
                Event(
                    id = 0L,
                    title = "A 이벤트",
                    venue = "A 장소",
                    startsAt = ZonedDateTime.now().plusDays(1),
                    status = EventStatus.SCHEDULED,
                    ownerId = ownerA.id,
                )
            )

            val ownerBToken = login("isolation-owner-b@example.com", ownerBPassword)

            When("[S-01] Owner B가 GET /api/facility-owner/facilities 를 조회하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(ownerBToken)),
                    String::class.java,
                )

                Then("[S-01] Owner A의 시설이 응답에 0건 포함된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    val content = body["content"]
                    val aFacilityInBResponse = content.any { node ->
                        node["name"].asText() == "A 시설"
                    }
                    aFacilityInBResponse shouldBe false
                }
            }

            When("[S-01] Owner B가 GET /api/goods-seller/products 를 조회하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/goods-seller/products",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(ownerBToken)),
                    String::class.java,
                )

                Then("[S-01] Owner A의 상품이 응답에 0건 포함된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    val content = body["content"]
                    val aProductInBResponse = content.any { node ->
                        node["name"].asText() == "A 상품"
                    }
                    aProductInBResponse shouldBe false
                }
            }

            When("[S-01] Owner B가 GET /api/event-host/events 를 조회하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(ownerBToken)),
                    String::class.java,
                )

                Then("[S-01] Owner A의 이벤트가 응답에 0건 포함된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)
                    val content = body["content"]
                    val aEventInBResponse = content.any { node ->
                        node["title"].asText() == "A 이벤트"
                    }
                    aEventInBResponse shouldBe false
                }
            }
        }
    }
}
