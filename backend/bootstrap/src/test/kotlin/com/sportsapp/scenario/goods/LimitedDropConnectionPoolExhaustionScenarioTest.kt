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
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.sql.Connection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

private const val OWNER_USER_ID = 9_100L
private const val BUYER_USER_ID = 9_101L

/**
 * 풀 크기를 2로 두면 [AdmissionPumpScheduler] 등 `@Scheduled` 백그라운드 잡이 두 커넥션
 * 사이에서 경합하다 테스트 리프 사이(설정 단계)에도 풀을 우연히 점유해 플레이키해진다
 * (실측 확인). 5로 늘려 백그라운드 잡이 숨 쉴 여지를 주고, 고갈이 필요한 순간에는 이
 * 상수만큼 raw 커넥션을 모두 점유해 여전히 완전 고갈을 재현한다.
 */
private const val TEST_POOL_SIZE = 5

/**
 * FIX-03 — HikariCP 커넥션 풀이 실제로 고갈됐을 때:
 *   1) 요청이 (구) 30초가 아니라 connection-timeout(5s) 남짓에 실패 응답으로 끝나고
 *   2) 503 + Retry-After 헤더로 응답하며 (500 애플리케이션 오류와 분리)
 *   3) 트랜잭션 자체가 시작되지 못하므로 주문이 생성되지 않는다 (커밋 후 응답 유실 제거, 핵심)
 * 는 것을 실 MySQL Hikari 풀 위에서 검증한다.
 *
 * 풀을 maximum-pool-size=5(TEST_POOL_SIZE)로 두고 그 전체를 테스트 스레드가 raw JDBC로 직접 점유한 뒤
 * 커넥션이 필요한 요청을 보낸다 — [BaseIntegrationTest]의 공유 캐시 컨텍스트와 다른 hikari
 * 프로퍼티를 쓰므로 별도 Spring 컨텍스트가 뜬다(다른 테스트에 영향 없음).
 *
 * 구매 엔드포인트(`POST /limited-drops/{id}/orders`)는 [EntryTokenGateInterceptor]가
 * `virtualqueue.enabled` 플래그를 DB 폴백으로 조회한다 — 이 조회도 같은 Hikari 풀을 쓰므로
 * 풀 고갈 시 컨트롤러 진입 전에 한 번(최대 connection-timeout), 실제 구매 트랜잭션 시작 시
 * 또 한 번(최대 connection-timeout) 대기가 누적된다(실측 확인, 총 ~10초). 이 두 번째 지점은
 * 이 티켓의 소유 파일(EntryTokenGateInterceptor·FeatureFlagEvaluatorImpl)이 아니므로 그대로 두고,
 * 그 누적치가 nginx proxy_read_timeout(15s, lb.conf)보다 작다는 것으로 "LB가 포기하기 전에
 * 백엔드가 먼저 응답한다" 정합성을 검증한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=5000",
    ],
)
class LimitedDropConnectionPoolExhaustionScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
    @Autowired private val dataSource: DataSource,
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

        fun createDropBody(productId: Long, limitedQuantity: Int, perUserLimit: Int): String =
            objectMapper.writeValueAsString(
                mapOf(
                    "productId" to productId,
                    "openAt" to isoString(ZonedDateTime.now().minusMinutes(1)),
                    "closeAt" to isoString(ZonedDateTime.now().plusDays(1)),
                    "limitedQuantity" to limitedQuantity,
                    "perUserLimit" to perUserLimit,
                )
            )

        fun createDrop(productId: Long, limitedQuantity: Int, perUserLimit: Int): Long {
            val result = mockMvc.perform(
                post("/limited-drops")
                    .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(OWNER_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createDropBody(productId, limitedQuantity, perUserLimit))
            ).andExpect(status().isCreated).andReturn()
            return objectMapper.readTree(result.response.contentAsString).get("dropId").asLong()
        }

        fun countOrderItems(productId: Long): Long = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goods_order_items WHERE product_id = ?",
            Long::class.java,
            productId,
        ) ?: 0L

        Given("커넥션 풀 전체(size=5)가 다른 작업에 점유된 상태에서") {
            When("게이트 인터셉터를 거치지 않는 단순 쓰기 요청(한정판 개설)을 보내면") {
                Then("30초가 아니라 connection-timeout(5s) 남짓에 503 + Retry-After로 응답한다") {
                    val productId = createProductWithStock(quantity = 10)

                    // [code-review p3] 획득 루프를 try 밖에 두면, 중간 인덱스에서 획득이 실패했을 때
                    // 이미 얻은 커넥션이 close() 되지 않고 누수돼 이후 리프가 연쇄 실패한다.
                    // acquire를 try 안으로 넣고 finally에서 누적 리스트를 닫는다.
                    val heldConnections = mutableListOf<Connection>()
                    try {
                        repeat(TEST_POOL_SIZE) { heldConnections.add(dataSource.connection) }

                        val start = System.currentTimeMillis()
                        val result = mockMvc.perform(
                            post("/limited-drops")
                                .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(OWNER_USER_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createDropBody(productId, limitedQuantity = 10, perUserLimit = 5))
                        ).andReturn()
                        val elapsedMillis = System.currentTimeMillis() - start

                        result.response.status shouldBe 503
                        result.response.getHeader("Retry-After").shouldNotBeNull()
                        elapsedMillis shouldBeLessThan 7_000L
                    } finally {
                        heldConnections.forEach { it.close() }
                    }
                }
            }

            When("한정판 구매 요청이 커넥션을 필요로 하면") {
                Then("nginx read timeout(15s)보다 먼저 503으로 응답하고 Retry-After를 포함하며, 주문을 생성하지 않는다") {
                    val productId = createProductWithStock(quantity = 10)
                    val dropId = createDrop(productId = productId, limitedQuantity = 10, perUserLimit = 5)
                    val idempotencyKey = UUID.randomUUID().toString()

                    // 풀(size=5) 전체를 raw JDBC 커넥션으로 점유 — 이후 어떤 트랜잭션도 커넥션을
                    // 얻지 못하고 connection-timeout(5s) 후 CannotCreateTransactionException.
                    // [code-review p3] 획득 루프를 try 안으로 넣어, 중간 획득 실패 시에도 이미 얻은
                    // 커넥션이 finally에서 확실히 반납되게 한다(누수 방지).
                    val heldConnections = mutableListOf<Connection>()
                    val (elapsedMillis, result) = try {
                        repeat(TEST_POOL_SIZE) { heldConnections.add(dataSource.connection) }
                        val start = System.currentTimeMillis()
                        val response = mockMvc.perform(
                            post("/limited-drops/$dropId/orders")
                                .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(BUYER_USER_ID))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mapOf("quantity" to 1)))
                        ).andReturn()
                        (System.currentTimeMillis() - start) to response
                    } finally {
                        // countOrderItems 검증은 커넥션을 반납한 뒤(풀이 정상화된 뒤) 실행해야 한다 —
                        // 점유 상태로 조회하면 그 조회 자체가 CannotGetJdbcConnectionException으로 실패한다.
                        heldConnections.forEach { it.close() }
                    }

                    result.response.status shouldBe 503
                    result.response.getHeader("Retry-After").shouldNotBeNull()
                    elapsedMillis shouldBeLessThan 15_000L
                    countOrderItems(productId) shouldBe 0L
                }
            }
        }
    }
}
