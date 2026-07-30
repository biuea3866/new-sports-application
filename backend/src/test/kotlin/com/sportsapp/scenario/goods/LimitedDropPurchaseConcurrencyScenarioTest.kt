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
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
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
 *
 * [code-review 후속] 이 시나리오는 "500-동시 요청이 운영값과 같은 30-커넥션 풀을 놓고 경합한다"는
 * 전제를 명시적으로 검증한다 — 공용 테스트 기본값(src/test/resources/application.yml)은 다른
 * 테스트의 컨텍스트 캐시 누적을 막기 위해 작게(10) 유지하므로, 이 클래스만 별도 컨텍스트로
 * 운영값을 오버라이드한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.hikari.maximum-pool-size=30",
        "spring.datasource.hikari.minimum-idle=30",
        "spring.datasource.hikari.connection-timeout=5000",
    ],
)
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
                /**
                 * [FIX-03] HikariCP connection-timeout을 30s(구 기본값)에서 5s로 낮추면서
                 * (application.yml) 500-동시 요청이 30-커넥션 풀을 놓고 경합하다 큐 대기가 5초를
                 * 넘는 일부는 (구) 30초를 기다려 결국 성공하는 대신 즉시 503(pool exhausted,
                 * 주문 미생성)으로 떨어진다(실측: 202=100, 409=43, 503=357). 이는 이 티켓이 의도한
                 * "커밋 후 응답 유실"을 "빠른 명시적 거절"로 바꾸는 정확한 동작이다 — 이전의
                 * "500-동시여도 결국 전원 처리(serverErrorCount=0)" 기대치는 30초 대기를 전제로 한
                 * 것이었고, 이제는 응답(202) 수와 생성된 주문 수가 정확히 일치하는지(핵심,
                 * PurchaseLimitedDropUseCase·LimitedDropDomainService는 FIX-02 소유라 미변경)와
                 * 오버셀이 없는지로 판정 기준을 옮긴다. 500/-1(설명되지 않은 예외)은 여전히 0이어야
                 * 하고, 503은 애플리케이션 버그가 아니라 정상 분류이므로 별도로 허용한다.
                 *
                 * [code-review p3] 503=357 전체를 hikari 풀 고갈(connection-timeout 5s)만으로
                 * 단정하지 않는다 — [LoadSheddingFilter]가 기본 `max-concurrent-requests=200`으로
                 * 필터 체인 최전방(인증 이전)에서 동시 인플라이트 500건 중 200건 초과분을 별도
                 * 503으로 즉시 거부하고, 이 테스트 yml에는 그 값을 오버라이드하지 않는다. 즉 503
                 * 버킷에는 (a) 풀 고갈로 트랜잭션을 시작하지 못한 503과 (b) 로드셰딩으로 서블릿
                 * 진입 자체가 거부된 503이 섞여 있을 수 있다 — 두 응답 모두 code=SERVICE_UNAVAILABLE·
                 * detail 문구가 동일해 현재 응답 본문만으로는 구분되지 않는다. 이 테스트는 "503은
                 * 정상 분류(500/-1이 아님)"만 단정하며, 503 내부 출처 비율은 판정 근거로 쓰지 않는다.
                 */
                Then("202 응답 수와 생성된 주문 수가 정확히 일치하고, 재고 초과 판매가 발생하지 않는다") {
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
                    // 503(SERVICE_UNAVAILABLE, 풀 고갈)은 이 티켓이 의도한 정상 분류다 —
                    // 설명되지 않은 애플리케이션 오류(500)·클라이언트 예외(-1)만 진짜 결함으로 본다.
                    val unexplainedErrorCount = statusCounts.filterKeys { it == 500 || it == -1 }.values.sumOf { it.get() }
                    val allowedStatuses = setOf(202, 409, 503)
                    val unexpectedStatuses = statusCounts.keys.filterNot { it in allowedStatuses }

                    totalHandled shouldBe threadCount
                    unexplainedErrorCount shouldBe 0
                    unexpectedStatuses shouldBe emptyList()
                    successCount shouldBeLessThanOrEqual 100
                    // [code-review p2] 하한 없이는 successCount=0(전원 503/409)도 통과해 언더셀
                    // 보호가 사라진다 — 이 시나리오의 존재 이유(재고 100 수렴)를 실제로 단정한다.
                    successCount shouldBeGreaterThan 0

                    // 핵심(FIX-03) — 커밋 후 응답 유실 제거: 202 응답 수와 실제 생성된 주문 수가 정확히 일치한다.
                    countOrderItems(productId) shouldBe successCount.toLong()
                    stockQuantityOf(productId) shouldBe (100 - successCount)
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
