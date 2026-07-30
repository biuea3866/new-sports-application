package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.usecase.AddCartItemUseCase
import com.sportsapp.application.goods.usecase.ClearCartUseCase
import com.sportsapp.application.goods.usecase.GetMyCartUseCase
import com.sportsapp.application.goods.usecase.RemoveCartItemUseCase
import com.sportsapp.application.goods.usecase.UpdateCartItemUseCase
import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/** AUTH-04 — 장바구니 전 엔드포인트는 개인 데이터라 `@AuthenticationPrincipal UserPrincipal`로 식별한다. */
class CartApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        getMyCartUseCase: GetMyCartUseCase = mockk(),
        addCartItemUseCase: AddCartItemUseCase = mockk(),
        updateCartItemUseCase: UpdateCartItemUseCase = mockk(),
        removeCartItemUseCase: RemoveCartItemUseCase = mockk(),
        clearCartUseCase: ClearCartUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        CartApiController(getMyCartUseCase, addCartItemUseCase, updateCartItemUseCase, removeCartItemUseCase, clearCartUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    Given("본인 장바구니 조회 요청") {
        val getMyCartUseCase = mockk<GetMyCartUseCase>()
        every { getMyCartUseCase.execute(TEST_USER_ID) } returns Pair(Cart(userId = TEST_USER_ID), emptyList())
        val mockMvc = buildMockMvc(getMyCartUseCase = getMyCartUseCase)

        When("GET /cart/me 요청 시") {
            val result = mockMvc.perform(get("/cart/me"))

            Then("principal.id 기준으로 조회되고 200을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
                verify(exactly = 1) { getMyCartUseCase.execute(TEST_USER_ID) }
            }
        }
    }

    Given("장바구니에 상품을 담는 요청") {
        val addCartItemUseCase = mockk<AddCartItemUseCase>()
        every {
            addCartItemUseCase.execute(match { it.userId == TEST_USER_ID && it.productId == 1L })
        } returns Pair(Cart(userId = TEST_USER_ID), emptyList())
        val mockMvc = buildMockMvc(addCartItemUseCase = addCartItemUseCase)

        When("POST /cart/items 요청 시") {
            val result = mockMvc.perform(
                post("/cart/items").contentType(MediaType.APPLICATION_JSON).content("""{"productId":1,"quantity":2}"""),
            )

            Then("principal.id 로 담기고 200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { addCartItemUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 장바구니 항목 수량 변경 요청") {
        val updateCartItemUseCase = mockk<UpdateCartItemUseCase>()
        every {
            updateCartItemUseCase.execute(match { it.userId == TEST_USER_ID && it.itemId == 5L })
        } returns Pair(Cart(userId = TEST_USER_ID), emptyList())
        val mockMvc = buildMockMvc(updateCartItemUseCase = updateCartItemUseCase)

        When("PATCH /cart/items/5 요청 시") {
            val result = mockMvc.perform(patch("/cart/items/5").contentType(MediaType.APPLICATION_JSON).content("""{"quantity":3}"""))

            Then("200을 반환한다") {
                result.andExpect(status().isOk)
            }
        }
    }

    Given("본인 장바구니 항목 제거 요청") {
        val removeCartItemUseCase = mockk<RemoveCartItemUseCase>()
        every { removeCartItemUseCase.execute(TEST_USER_ID, 5L) } returns Pair(Cart(userId = TEST_USER_ID), emptyList())
        val mockMvc = buildMockMvc(removeCartItemUseCase = removeCartItemUseCase)

        When("DELETE /cart/items/5 요청 시") {
            val result = mockMvc.perform(delete("/cart/items/5"))

            Then("200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { removeCartItemUseCase.execute(TEST_USER_ID, 5L) }
            }
        }
    }

    Given("장바구니 전체 비우기 요청") {
        val clearCartUseCase = mockk<ClearCartUseCase>()
        every { clearCartUseCase.execute(TEST_USER_ID) } returns Unit
        val mockMvc = buildMockMvc(clearCartUseCase = clearCartUseCase)

        When("DELETE /cart 요청 시") {
            val result = mockMvc.perform(delete("/cart"))

            Then("204를 반환한다") {
                result.andExpect(status().isNoContent)
                verify(exactly = 1) { clearCartUseCase.execute(TEST_USER_ID) }
            }
        }
    }
})
