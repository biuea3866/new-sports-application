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
import org.springframework.data.redis.core.StringRedisTemplate
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

// [S-04] Dashboard 카운트 정합성 + Redis 캐시 검증
// facility 3건, event scheduled 1 + open 1, product active 3 + outOfStock 2 등록 후
// GET /api/operator/dashboard/summary 카운트가 실제 row 수와 일치하는지 검증.
// 캐시 미스 -> 히트 흐름도 검증.
class DashboardCountIntegrityScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val eventJpaRepository: EventJpaRepository,
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

    private fun getDashboard(accessToken: String) = restTemplate.exchange(
        "${baseUrl()}/api/operator/dashboard/summary",
        HttpMethod.GET,
        HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(accessToken) }),
        String::class.java,
    )

    init {
        Given("[S-04] owner가 facility 3건, event 2건(scheduled 1, open 1), product 5건(active 3, outOfStock 2) 등록 후") {
            val adminPassword = "DashAdmin1!"
            val admin = userDomainService.register("dash-integrity-admin@example.com", adminPassword)
            userDomainService.assignRole(adminId = admin.id, userId = admin.id, roleName = "ADMIN")

            val ownerPassword = "DashOwner1!"
            val owner = userDomainService.register("dash-integrity-owner@example.com", ownerPassword)
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "FACILITY_OWNER")
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "EVENT_HOST")
            userDomainService.assignRole(adminId = admin.id, userId = owner.id, roleName = "GOODS_SELLER")

            // 기존 데이터 정리
            mongoTemplate.remove(
                Query(Criteria.where("owner_user_id").`is`(owner.id)),
                Facility::class.java,
            )
            jdbcTemplate.execute("DELETE FROM stocks WHERE product_id IN (SELECT id FROM products WHERE owner_id = ${owner.id})")
            jdbcTemplate.execute("DELETE FROM products WHERE owner_id = ${owner.id}")
            jdbcTemplate.execute("DELETE FROM seats WHERE event_id IN (SELECT id FROM events WHERE owner_id = ${owner.id})")
            jdbcTemplate.execute("DELETE FROM events WHERE owner_id = ${owner.id}")
            stringRedisTemplate.delete("b2b:b2bDashboardSummary:${owner.id}")

            // Facility 3건 등록
            (1..3).forEach { index ->
                facilityRepository.save(
                    Facility.create(
                        FacilityAttributes(
                            code = "DASH-FAC-$index",
                            name = "대시보드 시설 $index",
                            gu = "강남구",
                            type = "수영장",
                            address = "서울시 강남구",
                            lat = 37.5,
                            lng = 127.0,
                            parking = true,
                            tel = "02-0000-000$index",
                            homePage = "",
                            eduYn = false,
                            meta = emptyMap(),
                        ),
                    ).also { it.assignOwner(owner.id) },
                )
            }

            // Event scheduled 1건
            eventJpaRepository.save(
                Event(
                    id = 0L,
                    title = "대시보드 예정 이벤트",
                    venue = "테스트 장소",
                    startsAt = ZonedDateTime.now().plusDays(7),
                    status = EventStatus.SCHEDULED,
                    ownerId = owner.id,
                )
            )

            // Event open 1건
            eventJpaRepository.save(
                Event(
                    id = 0L,
                    title = "대시보드 오픈 이벤트",
                    venue = "테스트 장소",
                    startsAt = ZonedDateTime.now().plusDays(3),
                    status = EventStatus.OPEN,
                    ownerId = owner.id,
                )
            )

            // Product active 3건
            (1..3).forEach { index ->
                val product = productJpaRepository.save(
                    Product(
                        name = "활성 상품 $index",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("10000"),
                        description = "active product $index",
                        imageUrl = "https://example.com/active$index.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = owner.id,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 10))
            }

            // Product outOfStock 2건 (active지만 stock=0)
            (1..2).forEach { index ->
                val product = productJpaRepository.save(
                    Product(
                        name = "재고없음 상품 $index",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("5000"),
                        description = "out of stock product $index",
                        imageUrl = "https://example.com/oos$index.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = owner.id,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 0))
            }

            val accessToken = login("dash-integrity-owner@example.com", ownerPassword)
            val cacheKey = "b2b:b2bDashboardSummary:${owner.id}"

            When("[S-04] 첫 번째 GET /api/operator/dashboard/summary 호출 (캐시 미스)") {
                stringRedisTemplate.delete(cacheKey)
                val response = getDashboard(accessToken)

                Then("[S-04] 카운트가 실제 row 수와 일치하고 캐시가 저장된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(response.body)

                    val facilities = body["facilities"]
                    facilities["count"].asLong() shouldBe 3L

                    val events = body["events"]
                    events["scheduled"].asLong() shouldBe 1L
                    events["open"].asLong() shouldBe 1L

                    val products = body["products"]
                    products["active"].asLong() shouldBe 5L
                    products["outOfStock"].asLong() shouldBe 2L

                    stringRedisTemplate.hasKey(cacheKey) shouldBe true
                }
            }

            When("[S-04] 두 번째 GET /api/operator/dashboard/summary 호출 (캐시 히트)") {
                val firstResponse = getDashboard(accessToken)
                val secondResponse = getDashboard(accessToken)

                Then("[S-04] 두 응답 모두 200 OK이고 캐시 키가 존재한다") {
                    firstResponse.statusCode shouldBe HttpStatus.OK
                    secondResponse.statusCode shouldBe HttpStatus.OK
                    stringRedisTemplate.hasKey(cacheKey) shouldBe true
                }
            }

            When("[S-04] 캐시 수동 evict 후 재조회 시") {
                stringRedisTemplate.delete(cacheKey)
                val afterEvictResponse = getDashboard(accessToken)

                Then("[S-04] 캐시 미스 후 정상 응답이 반환되고 캐시가 재저장된다") {
                    afterEvictResponse.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(afterEvictResponse.body)
                    body["facilities"]["count"].asLong() shouldBe 3L
                    stringRedisTemplate.hasKey(cacheKey) shouldBe true
                }
            }
        }
    }
}
