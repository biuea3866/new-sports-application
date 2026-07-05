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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.ZonedDateTime

@AutoConfigureMockMvc
class ProductSearchScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val limitedDropJpaRepository: LimitedDropJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("FOOTWEAR 카테고리 러닝화 2건, APPAREL 반팔 1건이 저장된 상태") {
            jdbcTemplate.execute("TRUNCATE TABLE limited_drops")
            jdbcTemplate.execute("TRUNCATE TABLE stocks")
            jdbcTemplate.execute("TRUNCATE TABLE products")

            val runningShoe1 = productJpaRepository.save(
                Product(
                    name = "나이키 러닝화",
                    category = ProductCategory.FOOTWEAR,
                    price = BigDecimal("89000"),
                    description = "러닝 전용",
                    imageUrl = "https://example.com/1.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            val runningShoe2 = productJpaRepository.save(
                Product(
                    name = "아디다스 러닝화",
                    category = ProductCategory.FOOTWEAR,
                    price = BigDecimal("120000"),
                    description = "마라톤 전용",
                    imageUrl = "https://example.com/2.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            val tshirt = productJpaRepository.save(
                Product(
                    name = "스포츠 반팔",
                    category = ProductCategory.APPAREL,
                    price = BigDecimal("35000"),
                    description = "쿨링 소재",
                    imageUrl = "https://example.com/3.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            stockJpaRepository.save(Stock(productId = runningShoe1.id, quantity = 10))
            stockJpaRepository.save(Stock(productId = runningShoe2.id, quantity = 0))
            stockJpaRepository.save(Stock(productId = tshirt.id, quantity = 5))
            val openDrop = limitedDropJpaRepository.saveAndFlush(
                LimitedDrop.create(
                    productId = runningShoe1.id,
                    openAt = ZonedDateTime.now().minusHours(1),
                    closeAt = ZonedDateTime.now().plusDays(1),
                    limitedQuantity = 10,
                    perUserLimit = 1,
                ).also { it.open() }
            )

            When("[S-01] GET /products?category=FOOTWEAR&keyword=러닝&sort=price 요청 시") {
                val response = mockMvc.perform(
                    get("/products")
                        .param("category", "FOOTWEAR")
                        .param("keyword", "러닝")
                        .param("sort", "price")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK, 가격 오름차순 2건이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(2))
                        .andExpect(jsonPath("$.content[0].price").value(89000))
                        .andExpect(jsonPath("$.content[1].price").value(120000))
                }
            }

            When("[S-02] GET /products?category=FOOTWEAR 요청 시") {
                val response = mockMvc.perform(
                    get("/products")
                        .param("category", "FOOTWEAR")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("재고 0인 상품도 stockQuantity=0으로 응답에 포함된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(2))
                        .andExpect(jsonPath("$.content[?(@.stockQuantity == 0)]").isArray)
                }
            }

            When("[S-03] GET /products?page=0&size=2 페이지네이션 요청 시") {
                val response = mockMvc.perform(
                    get("/products")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("totalElements=3, totalPages=2, content 2건이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.totalPages").value(2))
                        .andExpect(jsonPath("$.content.length()").value(2))
                }
            }

            When("[S-04] GET /products?keyword=나이키 요청 시 (활성 회차가 연결된 상품)") {
                val response = mockMvc.perform(
                    get("/products")
                        .param("keyword", "나이키")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("content[0].limitedDropId에 활성 회차 id가 채워진다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content[0].id").value(runningShoe1.id))
                        .andExpect(jsonPath("$.content[0].limitedDropId").value(openDrop.id))
                }
            }

            When("[S-05] GET /products?keyword=아디다스 요청 시 (활성 회차가 없는 상품)") {
                val response = mockMvc.perform(
                    get("/products")
                        .param("keyword", "아디다스")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("content[0].limitedDropId는 존재하지 않는다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content[0].id").value(runningShoe2.id))
                        .andExpect(jsonPath("$.content[0].limitedDropId").doesNotExist())
                }
            }
        }
    }
}
