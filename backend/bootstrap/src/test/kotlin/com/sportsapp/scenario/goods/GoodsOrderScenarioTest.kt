package com.sportsapp.scenario.goods

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.infrastructure.goods.mysql.GoodsOrderJpaRepository
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

/** AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`로 본인 식별한다. */
@AutoConfigureMockMvc
class GoodsOrderScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    init {
        Given("재고 10개짜리 ACTIVE 상품이 있을 때") {
            lateinit var productId: String

            beforeEach {
                jdbcTemplate.execute("DELETE FROM goods_order_items")
                jdbcTemplate.execute("DELETE FROM goods_orders")
                jdbcTemplate.execute("DELETE FROM payments")
                jdbcTemplate.execute("DELETE FROM stocks")
                jdbcTemplate.execute("DELETE FROM products")

                val product = productJpaRepository.save(
                    Product(
                        name = "테니스 라켓",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("50000"),
                        description = "프리미엄 라켓",
                        imageUrl = "https://example.com/racket.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 10))
                productId = product.id.toString()
            }

            When("[S-01] POST /goods-orders로 주문 생성하면") {
                Then("202 Accepted와 orderId, paymentId, paymentStatus가 반환되고 재고가 차감된다") {
                    val result = mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":3}]}""")
                    ).andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.id").isNumber)
                        .andExpect(jsonPath("$.paymentId").isNumber)
                        .andExpect(jsonPath("$.paymentStatus").isString)
                        .andReturn()

                    val orderId = objectMapper.readTree(result.response.contentAsString)
                        .get("id").asLong()

                    val stock = stockJpaRepository.findByProductId(productId.toLong())
                    requireNotNull(stock)
                    stock.quantity shouldBe 7

                    mockMvc.perform(
                        get("/goods-orders/$orderId")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.items[0].quantity").value(3))
                }
            }

            When("[S-02] 직구매(카트 미경유)로 POST /goods-orders 호출하면") {
                Then("202 Accepted와 orderId가 반환된다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(2L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":1}]}""")
                    ).andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.id").isNumber)
                }
            }

            When("[S-03] 재고 소진 후 추가 주문을 시도하면") {
                Then("409 응답이 반환된다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(3L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":10}]}""")
                    ).andExpect(status().isAccepted)

                    mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(4L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":1}]}""")
                    ).andExpect(status().isConflict)
                }
            }

            When("[S-list] GET /goods-orders/me로 내 주문 목록 조회하면") {
                Then("해당 userId의 주문 목록만 반환되고 상태가 PENDING이다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(5L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":1}]}""")
                    ).andExpect(status().isAccepted)

                    mockMvc.perform(
                        get("/goods-orders/me")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(5L))
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.content.length()").value(1))
                        .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                }
            }

            When("[S-03] 주문 생성 후 GET /goods-orders/{id}로 현재 상태를 조회하면") {
                Then("PENDING 상태의 주문이 조회된다") {
                    val result = mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(6L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":1}]}""")
                    ).andExpect(status().isAccepted)
                        .andReturn()

                    val orderId = objectMapper.readTree(result.response.contentAsString)
                        .get("id").asLong()

                    mockMvc.perform(
                        get("/goods-orders/$orderId")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(6L))
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andExpect(jsonPath("$.id").value(orderId))
                }
            }
        }

        Given("빈 items로 주문 시도할 때") {
            When("[U-01] POST /goods-orders에 빈 items를 보내면") {
                Then("400 Bad Request가 반환된다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[]}""")
                    ).andExpect(status().isBadRequest)
                }
            }
        }

        Given("동일 Idempotency-Key로 POST /goods-orders를 두 번 호출할 때") {
            lateinit var productId: String
            val idempotencyKey = "idem-goods-order-e2e-06-r02"

            beforeEach {
                jdbcTemplate.execute("DELETE FROM goods_order_items")
                jdbcTemplate.execute("DELETE FROM goods_orders")
                jdbcTemplate.execute("DELETE FROM payments")
                jdbcTemplate.execute("DELETE FROM stocks")
                jdbcTemplate.execute("DELETE FROM products")

                val product = productJpaRepository.save(
                    Product(
                        name = "멱등 테스트 상품",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("30000"),
                        description = "멱등성 검증용",
                        imageUrl = "https://example.com/idem.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 10))
                productId = product.id.toString()
            }

            When("[E2E-06-R02] 동일 Idempotency-Key로 /goods-orders 재호출 시 동일 order id 반환") {
                Then("두 번째 호출은 새 주문을 생성하지 않고 첫 번째 응답과 동일한 order id를 반환한다") {
                    val requestBody = """{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":2}]}"""

                    val firstResult = mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(10L))
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                    ).andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.id").isNumber)
                        .andReturn()

                    val firstOrderId = objectMapper.readTree(firstResult.response.contentAsString)
                        .get("id").asLong()

                    val secondResult = mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(10L))
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                    ).andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.id").isNumber)
                        .andReturn()

                    val secondOrderId = objectMapper.readTree(secondResult.response.contentAsString)
                        .get("id").asLong()

                    secondOrderId shouldBe firstOrderId

                    val orderCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM goods_orders WHERE user_id = 10",
                        Long::class.java,
                    )
                    orderCount shouldBe 1L
                }
            }
        }
    }
}
