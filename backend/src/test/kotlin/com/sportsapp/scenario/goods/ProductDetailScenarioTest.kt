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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@AutoConfigureMockMvc
class ProductDetailScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("활성 상품과 재고가 저장된 상태") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            val product = productJpaRepository.save(
                Product(
                    name = "테니스 라켓",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("89000"),
                    description = "프리미엄 라켓",
                    imageUrl = "https://example.com/racket.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = 15))

            When("[S-01] GET /products/{id} 존재하는 상품 id 요청 시") {
                val response = mockMvc.perform(
                    get("/products/${product.id}")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK, 가격/재고/판매상태를 포함한 상품 상세가 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.id").value(product.id))
                        .andExpect(jsonPath("$.name").value("테니스 라켓"))
                        .andExpect(jsonPath("$.price").value(89000))
                        .andExpect(jsonPath("$.stockQuantity").value(15))
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .andExpect(jsonPath("$.category").value("EQUIPMENT"))
                }
            }
        }

        Given("soft-delete된 상품이 저장된 상태") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            val deletedProduct = productJpaRepository.save(
                Product(
                    name = "삭제된 라켓",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("50000"),
                    description = "삭제됨",
                    imageUrl = "https://example.com/deleted.jpg",
                    status = ProductStatus.INACTIVE,
                    ownerId = 1L,
                )
            )
            deletedProduct.softDelete(null)
            productJpaRepository.save(deletedProduct)

            When("[S-02] GET /products/{id} soft-delete된 상품 id 요청 시") {
                val response = mockMvc.perform(
                    get("/products/${deletedProduct.id}")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("404 Not Found가 반환된다") {
                    response.andExpect(status().isNotFound)
                }
            }
        }

        Given("존재하지 않는 상품 id로 요청 시") {
            When("[S-03] GET /products/999999 요청 시") {
                val response = mockMvc.perform(
                    get("/products/999999")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("404 Not Found가 반환된다") {
                    response.andExpect(status().isNotFound)
                }
            }
        }
    }
}
