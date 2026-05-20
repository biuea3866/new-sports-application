package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import com.sportsapp.infrastructure.persistence.goods.StockJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@AutoConfigureMockMvc
class CartScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("재고 5개짜리 ACTIVE 상품이 있을 때") {
            lateinit var productId: String

            beforeEach {
                jdbcTemplate.execute("DELETE FROM cart_items")
                jdbcTemplate.execute("DELETE FROM carts")
                jdbcTemplate.execute("DELETE FROM stocks")
                jdbcTemplate.execute("DELETE FROM products")

                val product = productJpaRepository.save(
                    Product(
                        name = "농구공",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("30000"),
                        description = "실내용",
                        imageUrl = "https://example.com/ball.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 5))
                productId = product.id.toString()
            }

            When("[S-03 비우기] POST /cart/items 후 DELETE /cart 호출하면") {
                Then("GET /cart/me는 빈 결과를 반환한다") {
                    mockMvc.perform(
                        post("/cart/items")
                            .header("X-User-Id", "100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"productId": $productId, "quantity": 2}""")
                    ).andExpect(status().isOk)

                    mockMvc.perform(
                        delete("/cart")
                            .header("X-User-Id", "100")
                    ).andExpect(status().isNoContent)

                    mockMvc.perform(
                        get("/cart/me")
                            .header("X-User-Id", "100")
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.items").isArray)
                        .andExpect(jsonPath("$.items.length()").value(0))
                }
            }

            When("[S-01 재고 초과] 재고 5개 상품을 10개 담으려고 하면") {
                Then("409 응답이 반환되고 장바구니는 변경되지 않는다") {
                    mockMvc.perform(
                        post("/cart/items")
                            .header("X-User-Id", "101")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"productId": $productId, "quantity": 10}""")
                    ).andExpect(status().isConflict)

                    mockMvc.perform(
                        get("/cart/me")
                            .header("X-User-Id", "101")
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.items.length()").value(0))
                }
            }

            When("[U-02 병합] 동일 상품을 2회 추가하면") {
                Then("quantity가 병합되고 row가 하나만 생성된다") {
                    mockMvc.perform(
                        post("/cart/items")
                            .header("X-User-Id", "102")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"productId": $productId, "quantity": 2}""")
                    ).andExpect(status().isOk)

                    mockMvc.perform(
                        post("/cart/items")
                            .header("X-User-Id", "102")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"productId": $productId, "quantity": 1}""")
                    ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.items[0].quantity").value(3))
                }
            }

            When("[S-02 인가] 다른 userId의 cartItem에 PATCH를 시도하면") {
                Then("403 응답이 반환된다") {
                    val addResponse = mockMvc.perform(
                        post("/cart/items")
                            .header("X-User-Id", "103")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"productId": $productId, "quantity": 1}""")
                    ).andExpect(status().isOk)
                        .andReturn()

                    val responseBody = addResponse.response.contentAsString
                    val itemId = com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(responseBody)
                        .at("/items/0/id")
                        .asLong()

                    mockMvc.perform(
                        patch("/cart/items/$itemId")
                            .header("X-User-Id", "999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"quantity": 5}""")
                    ).andExpect(status().isForbidden)
                }
            }
        }
    }
}
