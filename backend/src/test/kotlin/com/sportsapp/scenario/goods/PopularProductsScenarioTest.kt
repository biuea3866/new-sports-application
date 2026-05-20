package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.PopularProductsCache
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@AutoConfigureMockMvc
class PopularProductsScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val popularProductsCache: PopularProductsCache,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("[S-01] FOOTWEAR 카테고리 활성 상품 3건이 저장된 상태") {
            jdbcTemplate.execute("TRUNCATE TABLE stocks")
            jdbcTemplate.execute("TRUNCATE TABLE products")
            stringRedisTemplate.unlink("popular:products:FOOTWEAR")

            repeat(3) { index ->
                productJpaRepository.save(
                    Product(
                        name = "러닝화 $index",
                        category = ProductCategory.FOOTWEAR,
                        price = BigDecimal("${(index + 1) * 10000}"),
                        description = "desc",
                        imageUrl = "https://example.com/${index}.jpg",
                        status = ProductStatus.ACTIVE,
                    )
                )
            }

            When("GET /products/popular?category=FOOTWEAR 첫 번째 호출 시") {
                val response = mockMvc.perform(
                    get("/products/popular")
                        .param("category", "FOOTWEAR")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK + 3건 반환 및 캐시에 저장된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.length()").value(3))

                    val cached = popularProductsCache.get(ProductCategory.FOOTWEAR)
                    cached?.size shouldBe 3
                }
            }

            When("[S-01] GET /products/popular?category=FOOTWEAR 두 번째 호출 시 (캐시 hit)") {
                val response = mockMvc.perform(
                    get("/products/popular")
                        .param("category", "FOOTWEAR")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK + 3건 반환 (캐시로 응답)") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.length()").value(3))
                }
            }
        }

        Given("[S-02] APPAREL 캐시가 존재하는 상태") {
            val category = ProductCategory.APPAREL
            stringRedisTemplate.unlink("popular:products:${category.name}")

            productJpaRepository.save(
                Product(
                    name = "스포츠 반팔",
                    category = category,
                    price = BigDecimal("35000"),
                    description = "desc",
                    imageUrl = "https://example.com/apparel.jpg",
                    status = ProductStatus.ACTIVE,
                )
            )

            mockMvc.perform(
                get("/products/popular").param("category", "APPAREL").accept(MediaType.APPLICATION_JSON)
            )

            When("DELETE /products/popular/cache?category=APPAREL 호출 시") {
                mockMvc.perform(
                    delete("/products/popular/cache")
                        .param("category", "APPAREL")
                )
                    .andExpect(status().isNoContent)

                Then("[S-02] 해당 카테고리 캐시가 invalidate된다") {
                    popularProductsCache.get(category).shouldBeNull()
                }
            }
        }
    }
}
