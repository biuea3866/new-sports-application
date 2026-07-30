package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.presentation.message.dto.response.RoomResponse
import com.sportsapp.presentation.user.dto.response.LoginResponse
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * `POST /products/{productId}/chat` — goods 거래 채팅 생성 (BE-11, TDD FR-18).
 * `/products` 하위 전체 경로는 SecurityConfig 상 permitAll 이지만, buyerId(principal.id) 식별이
 * 필요해 이 경로만 authenticated()로 먼저 매칭되도록 SecurityConfig 에 명시적 규칙을 추가했다.
 */
@AutoConfigureMockMvc
class ProductChatApiControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    private fun registerUser(email: String, password: String = "Password1!"): Long =
        userDomainService.register(email, password).id

    private fun login(email: String, password: String = "Password1!"): String {
        val body = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readValue(body, LoginResponse::class.java).accessToken
    }

    private fun seedProduct(ownerId: Long): Product = productJpaRepository.save(
        Product(
            name = "축구화",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("30000"),
            description = "설명",
            imageUrl = "https://example.com/shoes.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = ownerId,
        ),
    )

    private fun requestChat(token: String, productId: Long) =
        mockMvc.perform(
            post("/products/$productId/chat").header("Authorization", "Bearer $token"),
        )

    init {
        Given("판매자 소유 상품과 구매자가 등록된 상태") {
            val sellerEmail = "product-chat-seller@example.com"
            val buyerEmail = "product-chat-buyer@example.com"
            val sellerId = registerUser(sellerEmail)
            val buyerId = registerUser(buyerEmail)
            val sellerToken = login(sellerEmail)
            val buyerToken = login(buyerEmail)
            val product = seedProduct(sellerId)

            When("구매자가 POST /products/{id}/chat 을 호출하면") {
                val body = requestChat(buyerToken, product.id)
                    .andExpect(status().isCreated)
                    .andReturn().response.contentAsString
                val room = objectMapper.readValue(body, RoomResponse::class.java)

                Then("판매자와의 GOODS_PRODUCT 컨텍스트 방이 생성된다") {
                    room.contextType shouldBe RoomContextType.GOODS_PRODUCT
                }
            }

            When("동일 구매자가 같은 상품에 대해 재요청하면") {
                val firstBody = requestChat(buyerToken, product.id)
                    .andExpect(status().isCreated)
                    .andReturn().response.contentAsString
                val secondBody = requestChat(buyerToken, product.id)
                    .andExpect(status().isCreated)
                    .andReturn().response.contentAsString
                val firstRoom = objectMapper.readValue(firstBody, RoomResponse::class.java)
                val secondRoom = objectMapper.readValue(secondBody, RoomResponse::class.java)

                Then("새 방이 아닌 기존 방으로 이동한다 (중복 생성 없음)") {
                    secondRoom.id shouldBe firstRoom.id
                }
            }

            When("판매자 본인이 자신의 상품에 대해 채팅을 요청하면") {
                Then("403 이 반환된다 (self-chat 거부)") {
                    requestChat(sellerToken, product.id).andExpect(status().isForbidden)
                }
            }

            When("Authorization 헤더 없이 요청하면") {
                Then("401 이 반환된다") {
                    mockMvc.perform(post("/products/${product.id}/chat")).andExpect(status().isUnauthorized)
                }
            }

            When("생성된 거래 방을 참여자가 아닌 제3자가 조회하면") {
                val otherEmail = "product-chat-other@example.com"
                registerUser(otherEmail)
                val otherToken = login(otherEmail)
                val roomBody = requestChat(buyerToken, product.id)
                    .andExpect(status().isCreated)
                    .andReturn().response.contentAsString
                val room = objectMapper.readValue(roomBody, RoomResponse::class.java)

                Then("403 이 반환된다 (비참여자 접근 거부)") {
                    mockMvc.perform(
                        get("/rooms/${room.id}").header("Authorization", "Bearer $otherToken"),
                    ).andExpect(status().isForbidden)
                }
            }
        }

        Given("존재하지 않는 productId") {
            val buyerEmail = "product-chat-missing-buyer@example.com"
            registerUser(buyerEmail)
            val buyerToken = login(buyerEmail)

            When("POST /products/{id}/chat 을 호출하면") {
                Then("404 가 반환된다") {
                    requestChat(buyerToken, 999_999L).andExpect(status().isNotFound)
                }
            }
        }
    }
}
