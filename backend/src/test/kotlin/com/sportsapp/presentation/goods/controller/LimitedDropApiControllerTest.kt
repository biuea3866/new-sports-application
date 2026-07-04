package com.sportsapp.presentation.goods.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val OWNER_USER_ID = 900L
private const val BUYER_USER_ID = 901L
private const val OTHER_BUYER_USER_ID = 902L

/**
 * LimitedDropApiController — API 계약(TDD "API 계약") 매핑 검증.
 * limited-drop.enabled=true(test application.yml 기본값) 컨텍스트에서 실행한다.
 */
@AutoConfigureMockMvc
class LimitedDropApiControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        lateinit var productId: String

        beforeEach {
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
            stockJpaRepository.save(Stock(productId = product.id, quantity = 1000))
            productId = product.id.toString()
        }

        fun isoString(time: ZonedDateTime): String = time.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        fun createDropBody(
            openAt: ZonedDateTime,
            closeAt: ZonedDateTime,
            limitedQuantity: Int,
            perUserLimit: Int,
        ): String = objectMapper.writeValueAsString(
            mapOf(
                "productId" to productId.toLong(),
                "openAt" to isoString(openAt),
                "closeAt" to isoString(closeAt),
                "limitedQuantity" to limitedQuantity,
                "perUserLimit" to perUserLimit,
            )
        )

        fun createDrop(
            openAt: ZonedDateTime = ZonedDateTime.now().minusMinutes(1),
            closeAt: ZonedDateTime = ZonedDateTime.now().plusDays(1),
            limitedQuantity: Int = 10,
            perUserLimit: Int = 5,
        ): Long {
            val result = mockMvc.perform(
                post("/limited-drops")
                    .header("X-User-Id", OWNER_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createDropBody(openAt, closeAt, limitedQuantity, perUserLimit))
            ).andExpect(status().isCreated).andReturn()
            return objectMapper.readTree(result.response.contentAsString).get("dropId").asLong()
        }

        fun purchase(dropId: Long, userId: Long, idempotencyKey: String, quantity: Int = 1) = mockMvc.perform(
            post("/limited-drops/$dropId/orders")
                .header("X-User-Id", userId.toString())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("quantity" to quantity)))
        )

        Given("판매 시작 시각이 지난 한정판 회차") {
            When("POST /limited-drops/{dropId}/orders로 구매하면") {
                Then("202와 orderId·dropId·PENDING 상태를 반환한다") {
                    val dropId = createDrop()

                    purchase(dropId, BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.orderId").isNumber)
                        .andExpect(jsonPath("$.dropId").value(dropId))
                        .andExpect(jsonPath("$.status").value("PENDING"))
                }
            }
        }

        Given("판매 시작 시각이 아직 도래하지 않은 한정판 회차") {
            When("POST /limited-drops/{dropId}/orders로 구매하면") {
                Then("425와 openAt을 응답 본문에 포함한다") {
                    val openAt = ZonedDateTime.now().plusDays(1)
                    val dropId = createDrop(openAt = openAt, closeAt = openAt.plusDays(1))

                    purchase(dropId, BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect { result -> result.response.status shouldBe 425 }
                        .andExpect(jsonPath("$.properties.code").value("LIMITED_DROP_TOO_EARLY"))
                        .andExpect(jsonPath("$.properties.openAt").exists())
                }
            }
        }

        Given("한정 수량이 이미 소진된 한정판 회차") {
            When("추가 구매를 시도하면") {
                Then("409 SoldOut을 반환한다") {
                    val dropId = createDrop(limitedQuantity = 1, perUserLimit = 5)
                    purchase(dropId, BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isAccepted)

                    purchase(dropId, OTHER_BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.properties.code").value("LIMITED_DROP_SOLD_OUT"))
                }
            }
        }

        Given("1인 구매 한도에 도달한 사용자") {
            When("같은 사용자가 다른 idempotencyKey로 재구매를 시도하면") {
                Then("403 PerUserLimit을 반환한다") {
                    val dropId = createDrop(limitedQuantity = 10, perUserLimit = 1)
                    purchase(dropId, BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isAccepted)

                    purchase(dropId, BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isForbidden)
                        .andExpect(jsonPath("$.properties.code").value("LIMITED_DROP_PER_USER_LIMIT_EXCEEDED"))
                }
            }
        }

        Given("개설되어 조회 가능한 한정판 회차") {
            When("GET /limited-drops/{dropId}를 호출하면") {
                Then("200과 dropId·productId·status·remaining·perUserLimit을 반환한다") {
                    val dropId = createDrop(limitedQuantity = 20, perUserLimit = 3)

                    mockMvc.perform(get("/limited-drops/$dropId"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.dropId").value(dropId))
                        .andExpect(jsonPath("$.productId").value(productId.toLong()))
                        .andExpect(jsonPath("$.remaining").value(20))
                        .andExpect(jsonPath("$.perUserLimit").value(3))
                }
            }
        }

        Given("구매 시도가 섞인 한정판 회차") {
            When("GET /limited-drops/{dropId}/stats를 호출하면") {
                Then("200과 successCount·soldOutRejectCount·tooEarlyRejectCount를 반환한다") {
                    val dropId = createDrop(limitedQuantity = 1, perUserLimit = 5)
                    purchase(dropId, BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isAccepted)
                    purchase(dropId, OTHER_BUYER_USER_ID, UUID.randomUUID().toString())
                        .andExpect(status().isConflict)

                    mockMvc.perform(get("/limited-drops/$dropId/stats"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.successCount").value(1))
                        .andExpect(jsonPath("$.soldOutRejectCount").value(1))
                }
            }
        }
    }
}
