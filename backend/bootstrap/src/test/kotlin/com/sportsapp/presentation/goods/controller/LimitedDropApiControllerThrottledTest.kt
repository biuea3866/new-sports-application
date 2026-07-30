package com.sportsapp.presentation.goods.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val OWNER_USER_ID = 910L
private const val BUYER_USER_ID = 911L

/**
 * 완충 permit(FR-7) 소진 시 429 Throttled 매핑을 검증한다. 세마포어를 0으로 강제해야 해
 * [LimitedDropApiControllerTest]와 다른 Spring 컨텍스트(별도 프로퍼티)로 분리한다.
 *
 * AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`로 본인 식별한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "app.limited-drop.reservation.semaphore-permits=0",
        "app.limited-drop.reservation.acquire-timeout-millis=1",
    ]
)
class LimitedDropApiControllerThrottledTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    init {
        Given("완충 permit이 0으로 소진된 한정판 회차") {
            When("구매를 시도하면") {
                Then("429 Throttled를 반환하고 DB에 도달하지 않는다") {
                    jdbcTemplate.execute("DELETE FROM goods_order_items")
                    jdbcTemplate.execute("DELETE FROM goods_orders")
                    jdbcTemplate.execute("DELETE FROM limited_drops")
                    jdbcTemplate.execute("DELETE FROM stocks")
                    jdbcTemplate.execute("DELETE FROM products")

                    val product = productJpaRepository.save(
                        Product(
                            name = "한정판 스니커즈",
                            category = ProductCategory.FOOTWEAR,
                            price = BigDecimal("50000"),
                            description = "설명",
                            imageUrl = "https://example.com/sneaker.jpg",
                            status = ProductStatus.ACTIVE,
                            ownerId = OWNER_USER_ID,
                        )
                    )
                    stockJpaRepository.save(Stock(productId = product.id, quantity = 100))

                    val openAt = ZonedDateTime.now().minusMinutes(1)
                    val closeAt = ZonedDateTime.now().plusDays(1)
                    val createResult = mockMvc.perform(
                        post("/limited-drops")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(OWNER_USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "productId" to product.id,
                                        "openAt" to openAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                                        "closeAt" to closeAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                                        "limitedQuantity" to 10,
                                        "perUserLimit" to 5,
                                    )
                                )
                            )
                    ).andExpect(status().isCreated).andReturn()
                    val dropId = objectMapper.readTree(createResult.response.contentAsString).get("dropId").asLong()

                    val result = mockMvc.perform(
                        post("/limited-drops/$dropId/orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(BUYER_USER_ID))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"quantity":1}""")
                    )

                    result.andExpect { mvcResult -> mvcResult.response.status shouldBe 429 }
                        .andExpect(jsonPath("$.properties.code").value("LIMITED_DROP_THROTTLED"))
                }
            }
        }
    }
}
