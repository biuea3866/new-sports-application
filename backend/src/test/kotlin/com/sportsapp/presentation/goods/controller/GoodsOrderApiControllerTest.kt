package com.sportsapp.presentation.goods.controller

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.infrastructure.goods.mysql.GoodsOrderItemJpaRepository
import com.sportsapp.infrastructure.goods.mysql.GoodsOrderJpaRepository
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * GET /goods-orders/{orderId} — 주문 상세 응답 보강(Option A+) 검증.
 * 통합 주문내역 리스트(BE-08 `OrderCompositionService`가 소비하는 `GoodsOrderWithTitle`)만큼
 * 상세도 리치하도록, createdAt·대표 상품명(title)·항목별 productId를 함께 반환하는지 확인한다.
 */
@AutoConfigureMockMvc
class GoodsOrderApiControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val goodsOrderItemJpaRepository: GoodsOrderItemJpaRepository,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("DELETE FROM goods_order_items")
        jdbcTemplate.execute("DELETE FROM goods_orders")
        jdbcTemplate.execute("DELETE FROM products")
    }

    private fun saveProduct(name: String): Product =
        productJpaRepository.save(
            Product(
                name = name,
                category = ProductCategory.FOOTWEAR,
                price = BigDecimal("89000"),
                description = "설명",
                imageUrl = "https://example.com/img.jpg",
                status = ProductStatus.ACTIVE,
                sellerType = SellerType.B2C,
                ownerId = 1L,
            )
        )

    init {
        afterEach { resetData() }

        Given("본인 소유의 단일 상품 주문이 존재하는 상태") {
            resetData()
            val product = saveProduct("나이키 러닝화")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("89000")))
            val item = goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = order.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("89000"))
            )

            When("GET /goods-orders/{orderId}를 본인 X-User-Id로 호출하면") {
                Then("createdAt·대표 상품명(title)·항목별 productId가 함께 채워진 200 응답을 반환한다") {
                    mockMvc.perform(
                        get("/goods-orders/${order.id}")
                            .header("X-User-Id", "1")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.id").value(order.id))
                        .andExpect(jsonPath("$.title").value("나이키 러닝화"))
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.totalAmount").value(89000))
                        .andExpect(jsonPath("$.items[0].productId").value(product.id))
                        .andExpect(jsonPath("$.items[0].id").value(item.id))
                }
            }
        }

        Given("서로 다른 상품 2건으로 구성된 본인 소유 주문이 존재하는 상태") {
            resetData()
            val product1 = saveProduct("나이키 러닝화")
            val product2 = saveProduct("아디다스 반바지")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 2L, totalAmount = BigDecimal("124000")))
            goodsOrderItemJpaRepository.saveAll(
                listOf(
                    GoodsOrderItem(orderId = order.id, productId = product1.id, quantity = 1, unitPrice = BigDecimal("89000")),
                    GoodsOrderItem(orderId = order.id, productId = product2.id, quantity = 1, unitPrice = BigDecimal("35000")),
                )
            )

            When("GET /goods-orders/{orderId}를 본인 X-User-Id로 호출하면") {
                Then("대표 상품명 + 외 N건으로 title이 구성된다") {
                    mockMvc.perform(
                        get("/goods-orders/${order.id}")
                            .header("X-User-Id", "2")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.title").value("나이키 러닝화 외 1건"))
                        .andExpect(jsonPath("$.items.length()").value(2))
                }
            }
        }

        Given("타인 소유의 주문이 존재하는 상태") {
            resetData()
            val product = saveProduct("나이키 러닝화")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 3L, totalAmount = BigDecimal("89000")))
            goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = order.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("89000"))
            )

            When("GET /goods-orders/{orderId}를 타인 X-User-Id로 호출하면") {
                Then("403을 반환한다") {
                    mockMvc.perform(
                        get("/goods-orders/${order.id}")
                            .header("X-User-Id", "999")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isForbidden)
                }
            }
        }

        Given("존재하지 않는 orderId인 상태(엣지)") {
            resetData()

            When("GET /goods-orders/{orderId}를 호출하면") {
                Then("404를 반환한다") {
                    mockMvc.perform(
                        get("/goods-orders/999999")
                            .header("X-User-Id", "1")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isNotFound)
                }
            }
        }
    }
}
