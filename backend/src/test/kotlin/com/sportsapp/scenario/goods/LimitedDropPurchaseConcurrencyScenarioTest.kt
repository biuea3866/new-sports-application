package com.sportsapp.scenario.goods

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val OWNER_USER_ID = 9_000L

/**
 * 한정판 구매 흐름 전체(presentation→application→domain→infrastructure)를 실 MySQL·Redis(Testcontainers)
 * 위에서 검증하는 E2E 시나리오(BE-12, 근거: TDD "Testing Plan", PRD Success Metrics·M4).
 *
 * 재고 100·동시 500 요청 케이스가 핵심 — 오버셀 0과 성공 정확히 100건 수렴을 실 DB 경합으로 증명한다.
 *
 * 각 Then 블록 안에서 상품·회차를 생성한다 — Kotest BehaviorSpec은 Given 본문을 트리 구성 시
 * 1회만 평가하므로, beforeEach의 테이블 정리(리프마다 재실행)와 타이밍이 어긋나면 Given 레벨에서
 * 미리 만든 행이 실제 리프 실행 전에 삭제된다([LimitedDropApiControllerTest] 선례와 동일 원칙).
 */
@AutoConfigureMockMvc
class LimitedDropPurchaseConcurrencyScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
            jdbcTemplate.execute("DELETE FROM limited_drops")
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
        }

        fun isoString(time: ZonedDateTime): String = time.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        fun createProductWithStock(quantity: Int): Long {
            val product = productJpaRepository.save(
                Product(
                    name = "한정판 상품",
                    category = ProductCategory.FOOTWEAR,
                    price = BigDecimal("50000"),
                    description = "설명",
                    imageUrl = "https://example.com/sneaker.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = OWNER_USER_ID,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = quantity))
            return product.id
        }

        fun createDrop(
            productId: Long,
            openAt: ZonedDateTime = ZonedDateTime.now().minusMinutes(1),
            closeAt: ZonedDateTime = ZonedDateTime.now().plusDays(1),
            limitedQuantity: Int,
            perUserLimit: Int,
        ): Long {
            val body = objectMapper.writeValueAsString(
                mapOf(
                    "productId" to productId,
                    "openAt" to isoString(openAt),
                    "closeAt" to isoString(closeAt),
                    "limitedQuantity" to limitedQuantity,
                    "perUserLimit" to perUserLimit,
                )
            )
            val result = mockMvc.perform(
                post("/limited-drops")
                    .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(OWNER_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            ).andExpect(status().isCreated).andReturn()
            return objectMapper.readTree(result.response.contentAsString).get("dropId").asLong()
        }

        fun purchase(dropId: Long, userId: Long, idempotencyKey: String, quantity: Int = 1) = mockMvc.perform(
            post("/limited-drops/$dropId/orders")
                .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("quantity" to quantity)))
        )

        fun countOrderItems(productId: Long): Long = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goods_order_items WHERE product_id = ?",
            Long::class.java,
            productId,
        ) ?: 0L

        fun stockQuantityOf(productId: Long): Int = jdbcTemplate.queryForObject(
            "SELECT quantity FROM stocks WHERE product_id = ?",
            Int::class.java,
            productId,
        ) ?: -1

        fun buyerKey(dropId: Long, userId: Long) = "goods:limited-drop:$dropId:buyer:$userId"
        fun remainingKey(dropId: Long) = "goods:limited-drop:$dropId:remaining"

        Given("재고 100개로 개설된 한정판 회차와 서로 다른 유저 500명이 있을 때") {
            When("500명이 동시에 1개씩 구매를 시도하면") {
                Then("성공 주문이 정확히 100건이고 DB 재고가 0으로 수렴하며 오버셀이 발생하지 않는다") {
                    val productId = createProductWithStock(quantity = 100)
                    val dropId = createDrop(productId = productId, limitedQuantity = 100, perUserLimit = 1)

                    val threadCount = 500
                    val executor = Executors.newFixedThreadPool(threadCount)
                    val ready = CountDownLatch(threadCount)
                    val start = CountDownLatch(1)
                    val done = CountDownLatch(threadCount)
                    val statusCounts = ConcurrentHashMap<Int, AtomicInteger>()

                    repeat(threadCount) { index ->
                        executor.submit {
                            ready.countDown()
                            start.await()
                            val responseStatus = try {
                                purchase(dropId, 1_000_000L + index, UUID.randomUUID().toString()).andReturn()
                                    .response.status
                            } catch (exception: Throwable) {
                                -1
                            }
                            statusCounts.computeIfAbsent(responseStatus) { AtomicInteger(0) }.incrementAndGet()
                            done.countDown()
                        }
                    }

                    ready.await(10, TimeUnit.SECONDS)
                    start.countDown()
                    done.await(120, TimeUnit.SECONDS)
                    executor.shutdownNow()

                    val successCount = statusCounts[202]?.get() ?: 0
                    val totalHandled = statusCounts.values.sumOf { it.get() }
                    val serverErrorCount = statusCounts.filterKeys { it >= 500 || it == -1 }.values.sumOf { it.get() }

                    totalHandled shouldBe threadCount
                    serverErrorCount shouldBe 0
                    successCount shouldBe 100

                    stockQuantityOf(productId) shouldBe 0
                    countOrderItems(productId) shouldBe 100L
                }
            }
        }

        Given("한정 수량 1개로 이미 소진된 한정판 회차") {
            When("추가로 다른 유저가 구매를 시도하면") {
                Then("409 SoldOut을 반환하고 DB 주문 건수는 늘지 않는다") {
                    val productId = createProductWithStock(quantity = 10)
                    val dropId = createDrop(productId = productId, limitedQuantity = 1, perUserLimit = 5)
                    purchase(dropId, 2_000_000L, UUID.randomUUID().toString()).andExpect(status().isAccepted)

                    val ordersBefore = countOrderItems(productId)

                    purchase(dropId, 2_000_001L, UUID.randomUUID().toString())
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.properties.code").value("LIMITED_DROP_SOLD_OUT"))

                    countOrderItems(productId) shouldBe ordersBefore
                }
            }
        }

        Given("판매 시작 시각이 아직 도래하지 않은 한정판 회차") {
            When("구매를 시도하면") {
                Then("425와 openAt을 응답 본문에 포함한다") {
                    val productId = createProductWithStock(quantity = 10)
                    val openAt = ZonedDateTime.now().plusDays(1)
                    val dropId = createDrop(
                        productId = productId,
                        openAt = openAt,
                        closeAt = openAt.plusDays(1),
                        limitedQuantity = 10,
                        perUserLimit = 5,
                    )

                    purchase(dropId, 3_000_000L, UUID.randomUUID().toString())
                        .andExpect { result -> result.response.status shouldBe 425 }
                        .andExpect(jsonPath("$.properties.code").value("LIMITED_DROP_TOO_EARLY"))
                        .andExpect(jsonPath("$.properties.openAt").exists())
                }
            }
        }

        Given("동일 사용자가 동일 idempotencyKey로 반복 요청할 때") {
            When("같은 idempotencyKey로 두 번 연속 구매 요청하면") {
                Then("주문이 1건만 생성되고 두 응답의 orderId가 동일하다") {
                    val productId = createProductWithStock(quantity = 100)
                    val dropId = createDrop(productId = productId, limitedQuantity = 10, perUserLimit = 5)
                    val idempotencyKey = UUID.randomUUID().toString()

                    val first = purchase(dropId, 4_000_000L, idempotencyKey)
                        .andExpect(status().isAccepted)
                        .andReturn()
                    val second = purchase(dropId, 4_000_000L, idempotencyKey)
                        .andExpect(status().isAccepted)
                        .andReturn()

                    val firstOrderId = objectMapper.readTree(first.response.contentAsString).get("orderId").asLong()
                    val secondOrderId = objectMapper.readTree(second.response.contentAsString).get("orderId").asLong()
                    firstOrderId shouldBe secondOrderId

                    val orderCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM goods_orders WHERE idempotency_key = ?",
                        Long::class.java,
                        idempotencyKey,
                    )
                    orderCount shouldBe 1L
                }
            }
        }

        Given("Redis 입장 게이트가 장애 상태(예약 카운터 키 타입 손상 → reserve 호출 시 DataAccessException)인 한정판 회차") {
            When("Redis 예약 게이트 호출이 매번 실패하는 상태에서 30명이 동시에 구매를 시도하면") {
                Then("Stock 낙관적 락(@Version)만으로 재고 수량만큼만 성공하고 오버셀이 발생하지 않는다") {
                    val productId = createProductWithStock(quantity = 5)
                    val dropId = createDrop(productId = productId, limitedQuantity = 5, perUserLimit = 1)
                    val buyerUserIds = (1..30).map { 5_000_000L + it }
                    buyerUserIds.forEach { userId ->
                        val key = buyerKey(dropId, userId)
                        redisTemplate.delete(key)
                        redisTemplate.opsForList().leftPush(key, "corrupted-to-force-wrongtype-error")
                    }

                    val threadCount = buyerUserIds.size
                    val executor = Executors.newFixedThreadPool(threadCount)
                    val ready = CountDownLatch(threadCount)
                    val start = CountDownLatch(1)
                    val done = CountDownLatch(threadCount)
                    val statusCounts = ConcurrentHashMap<Int, AtomicInteger>()

                    buyerUserIds.forEach { userId ->
                        executor.submit {
                            ready.countDown()
                            start.await()
                            val responseStatus = try {
                                purchase(dropId, userId, UUID.randomUUID().toString()).andReturn().response.status
                            } catch (exception: Throwable) {
                                -1
                            }
                            statusCounts.computeIfAbsent(responseStatus) { AtomicInteger(0) }.incrementAndGet()
                            done.countDown()
                        }
                    }

                    ready.await(10, TimeUnit.SECONDS)
                    start.countDown()
                    done.await(60, TimeUnit.SECONDS)
                    executor.shutdownNow()

                    val successCount = statusCounts[202]?.get() ?: 0
                    val serverErrorCount = statusCounts.filterKeys { it >= 500 || it == -1 }.values.sumOf { it.get() }

                    serverErrorCount shouldBe 0
                    successCount shouldBe 5
                    stockQuantityOf(productId) shouldBe 0

                    // Redis remaining 카운터가 그대로(시드값 5)라는 것은 이 시나리오 전체가
                    // Redis 게이트를 거치지 않고 DB 낙관락만으로 오버셀을 막았다는 증거다.
                    redisTemplate.opsForValue().get(remainingKey(dropId)) shouldBe "5"
                }
            }
        }
    }
}
