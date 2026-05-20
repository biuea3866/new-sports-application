package com.sportsapp.scenario.goods

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.infrastructure.persistence.goods.GoodsOrderJpaRepository
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import com.sportsapp.infrastructure.persistence.goods.StockJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@AutoConfigureMockMvc
class GoodsOrderScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
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
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 10))
                productId = product.id.toString()
            }

            When("[S-01] POST /goods-orders로 주문 생성하면") {
                Then("202 Accepted와 orderId, paymentId, paymentStatus가 반환되고 재고가 차감된다") {
                    val result = mockMvc.perform(
                        post("/goods-orders")
                            .header("X-User-Id", "1")
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
                            .header("X-User-Id", "1")
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.items[0].quantity").value(3))
                }
            }

            When("[S-02] 직구매(카트 미경유)로 POST /goods-orders 호출하면") {
                Then("202 Accepted와 orderId가 반환된다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header("X-User-Id", "2")
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
                            .header("X-User-Id", "3")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":10}]}""")
                    ).andExpect(status().isAccepted)

                    mockMvc.perform(
                        post("/goods-orders")
                            .header("X-User-Id", "4")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":1}]}""")
                    ).andExpect(status().isConflict)
                }
            }

            When("[S-list] GET /goods-orders/me로 내 주문 목록 조회하면") {
                Then("해당 userId의 주문 목록만 반환된다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header("X-User-Id", "5")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[{"productId":$productId,"quantity":1}]}""")
                    ).andExpect(status().isAccepted)

                    mockMvc.perform(
                        get("/goods-orders/me")
                            .header("X-User-Id", "5")
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.content.length()").value(1))
                        .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                }
            }
        }

        Given("빈 items로 주문 시도할 때") {
            When("[U-01] POST /goods-orders에 빈 items를 보내면") {
                Then("400 Bad Request가 반환된다") {
                    mockMvc.perform(
                        post("/goods-orders")
                            .header("X-User-Id", "1")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"method":"CREDIT_CARD","fromCart":false,"items":[]}""")
                    ).andExpect(status().isBadRequest)
                }
            }
        }
    }
}
