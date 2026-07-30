package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.infrastructure.goods.mysql.LimitedDropJpaRepository
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.ZonedDateTime

@AutoConfigureMockMvc
class ProductDetailScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val limitedDropJpaRepository: LimitedDropJpaRepository,
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
                        .andExpect(jsonPath("$.limitedDropId").doesNotExist())
                }
            }
        }

        Given("활성 한정판 회차가 연결된 상품이 저장된 상태") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE limited_drops")
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            val product = productJpaRepository.save(
                Product(
                    name = "한정판 스니커즈",
                    category = ProductCategory.FOOTWEAR,
                    price = BigDecimal("120000"),
                    description = "한정판",
                    imageUrl = "https://example.com/sneaker.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = 30))
            val openDrop = LimitedDrop.create(
                productId = product.id,
                openAt = ZonedDateTime.now().minusHours(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = 30,
                perUserLimit = 2,
            ).also { it.open() }
            val savedDrop = limitedDropJpaRepository.saveAndFlush(openDrop)

            When("[S-04] GET /products/{id} 요청 시") {
                val response = mockMvc.perform(
                    get("/products/${product.id}")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK, 활성 회차의 limitedDropId를 포함한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.limitedDropId").value(savedDrop.id))
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
