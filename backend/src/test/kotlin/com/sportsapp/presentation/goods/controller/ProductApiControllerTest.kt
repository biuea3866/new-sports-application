package com.sportsapp.presentation.goods.controller

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

private const val OWNER_USER_ID = 950L

/**
 * [F5][F6] ProductApiController — 실측 부하 테스트에서 발견된 결함 재현·회귀 방지.
 *
 * - image_url 컬럼(V6 마이그레이션)은 NULL을 허용하지만, 응답 DTO는 non-null String으로
 *   선언돼 있어 Hibernate가 리플렉션으로 null 필드를 로드한 뒤 응답 매핑 시점에
 *   `Intrinsics.checkNotNullParameter`가 NPE를 던져 500이 됐다(F5). 실제 HTTP 계층까지 통과해
 *   200을 반환하는지 검증한다 — 앱 전역 @Primary ObjectMapper(McpObjectMapperConfig)가
 *   NON_NULL 직렬화이므로 imageUrl=null 필드는 응답 JSON에서 생략된다(존재해도 무방, 예외만 없으면 됨).
 * - /products/popular 는 category가 필수 @RequestParam인데, 누락 시 매칭되는 예외 핸들러가
 *   없어 500으로 떨어졌다(F6). GlobalExceptionHandler의 MissingServletRequestParameterException
 *   핸들러 도입 후 400을 반환하는지 실제 엔드포인트로 검증한다.
 *
 * beforeEach에서 상품을 매번 새로 생성한다 — Kotest는 leaf(Then)에 도달하기까지 경로상의
 * beforeEach를 여러 번 재실행하므로, Given 블록 본문에서 1회성으로 데이터를 만들면 이후
 * beforeEach가 테이블을 비우면서 leaf 실행 직전에 데이터가 사라진다(LimitedDropApiControllerTest와
 * 동일한 패턴).
 */
@AutoConfigureMockMvc
class ProductApiControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        var productId = 0L

        beforeEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
            jdbcTemplate.execute("DELETE FROM limited_drops")
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
            stringRedisTemplate.unlink("popular:products:${ProductCategory.ACCESSORY.name}")

            val saved = productJpaRepository.save(
                Product(
                    name = "이미지 없는 상품",
                    category = ProductCategory.ACCESSORY,
                    price = BigDecimal("15000"),
                    description = "설명",
                    imageUrl = "placeholder",
                    status = ProductStatus.ACTIVE,
                    ownerId = OWNER_USER_ID,
                )
            )
            jdbcTemplate.update("UPDATE products SET image_url = NULL WHERE id = ?", saved.id)
            stockJpaRepository.save(Stock(productId = saved.id, quantity = 10))
            productId = saved.id
        }

        Given("[F5] image_url이 NULL인 상품이 저장된 상태") {
            When("GET /products/{id} 로 단건 조회하면") {
                Then("500 대신 200을 반환한다 (imageUrl은 NON_NULL 직렬화 설정으로 응답에서 생략된다)") {
                    mockMvc.perform(get("/products/$productId").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.id").value(productId))
                        .andExpect(jsonPath("$.imageUrl").doesNotExist())
                }
            }

            When("GET /products 로 목록 조회하면") {
                Then("500 대신 200을 반환한다 (imageUrl은 NON_NULL 직렬화 설정으로 응답에서 생략된다)") {
                    mockMvc.perform(
                        get("/products")
                            .param("category", ProductCategory.ACCESSORY.name)
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content[0].id").value(productId))
                        .andExpect(jsonPath("$.content[0].imageUrl").doesNotExist())
                }
            }
        }

        Given("[F5] image_url이 NULL인 ACTIVE 상품이 인기 상품 후보인 상태") {
            When("GET /products/popular?category=ACCESSORY 를 호출하면") {
                Then("500 대신 200을 반환한다 (PopularProductSnapshot 매핑 NPE 없음)") {
                    mockMvc.perform(
                        get("/products/popular")
                            .param("category", ProductCategory.ACCESSORY.name)
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.length()").value(1))
                }
            }
        }

        Given("[F6] 필수 @RequestParam(category)이 없는 상태") {
            When("GET /products/popular 를 category 없이 호출하면") {
                Then("500 대신 400 + ProblemDetail(code=MISSING_REQUEST_PARAMETER) 을 반환한다") {
                    mockMvc.perform(get("/products/popular").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest)
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.properties.code").value("MISSING_REQUEST_PARAMETER"))
                        .andExpect(jsonPath("$.detail").value("Required request parameter is missing: category"))
                }
            }

            When("GET /products/popular?category=ACCESSORY 로 호출하면") {
                Then("파라미터가 있으면 200을 반환한다 (회귀)") {
                    mockMvc.perform(
                        get("/products/popular")
                            .param("category", ProductCategory.ACCESSORY.name)
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                }
            }
        }
    }
}
