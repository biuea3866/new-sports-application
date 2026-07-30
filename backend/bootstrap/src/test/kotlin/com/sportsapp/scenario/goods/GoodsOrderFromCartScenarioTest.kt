package com.sportsapp.scenario.goods

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.infrastructure.goods.mysql.CartItemJpaRepository
import com.sportsapp.infrastructure.goods.mysql.CartJpaRepository
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.infrastructure.payment.mysql.PaymentJpaRepository
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

/** AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`로 본인 식별한다. */
@AutoConfigureMockMvc
class GoodsOrderFromCartScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val cartJpaRepository: CartJpaRepository,
    @Autowired private val cartItemJpaRepository: CartItemJpaRepository,
    @Autowired private val paymentJpaRepository: PaymentJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    init {
        Given("카트에 상품 2종이 담긴 사용자가 있을 때") {
            val userId = 200L
            lateinit var productId1: String
            lateinit var productId2: String

            beforeEach {
                jdbcTemplate.execute("DELETE FROM goods_order_items")
                jdbcTemplate.execute("DELETE FROM goods_orders")
                jdbcTemplate.execute("DELETE FROM payments")
                jdbcTemplate.execute("DELETE FROM cart_items")
                jdbcTemplate.execute("DELETE FROM carts")
                jdbcTemplate.execute("DELETE FROM stocks")
                jdbcTemplate.execute("DELETE FROM products")

                val product1 = productJpaRepository.save(
                    Product(
                        name = "축구공",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("30000"),
                        description = "실외용",
                        imageUrl = "https://example.com/soccer.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                val product2 = productJpaRepository.save(
                    Product(
                        name = "농구공",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("40000"),
                        description = "실내용",
                        imageUrl = "https://example.com/basket.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product1.id, quantity = 10))
                stockJpaRepository.save(Stock(productId = product2.id, quantity = 5))
                productId1 = product1.id.toString()
                productId2 = product2.id.toString()

                mockMvc.perform(
                    post("/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"productId":$productId1,"quantity":2}""")
                ).andExpect(status().isOk)

                mockMvc.perform(
                    post("/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"productId":$productId2,"quantity":1}""")
                ).andExpect(status().isOk)
            }

            When("[S-cart-01] fromCart=true로 POST /goods-orders 호출하면") {
                Then("GoodsOrder PENDING + Payment 발급이 확인되고 주문은 PENDING 상태로 남는다") {
                    val idempotencyKey = UUID.randomUUID().toString()
                    val result = mockMvc.perform(
                        post("/goods-orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """{"method":"CREDIT_CARD","fromCart":true,"items":[
                                    {"productId":$productId1,"quantity":2},
                                    {"productId":$productId2,"quantity":1}
                                ]}"""
                            )
                    ).andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.id").isNumber)
                        .andExpect(jsonPath("$.paymentId").isNumber)
                        .andExpect(jsonPath("$.paymentStatus").isString)
                        .andReturn()

                    val responseTree = objectMapper.readTree(result.response.contentAsString)
                    val paymentId = responseTree.get("paymentId").asLong()

                    // Payment 발급 확인
                    val payment = paymentJpaRepository.findById(paymentId)
                    payment.isPresent shouldBe true
                    payment.get().idempotencyKey shouldBe idempotencyKey

                    // 주문 생성 시 카트는 아직 비워지지 않는다 (웹훅 확정 이후 처리)
                    val cart = cartJpaRepository.findByUserIdAndDeletedAtIsNull(userId)
                    cart shouldNotBe null
                    val activeCartId = requireNotNull(cart).id
                    val activeItems = cartItemJpaRepository.findAllByCartIdAndDeletedAtIsNull(activeCartId)
                    activeItems.size shouldBe 2
                }
            }
        }
    }
}
