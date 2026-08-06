package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryCriteria
import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryItemResponse
import com.sportsapp.application.goods.usecase.FindGoodsOrderHistoryUseCase
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.hamcrest.Matchers.nullValue
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import sportsapp.testkit.presentation.support.productionEquivalentJsonConverter

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * 통합 주문내역(BE-08)의 goods 원격 공급 엔드포인트 계약 검증 (S2-03).
 *
 * 계약 필드가 조용히 누락되는 사고가 S2-04·S2-05 에서 실제로 발생했으므로(소비자 계약 확장을 늦게
 * 반영), **JSON 레벨에서 필드 존재를 고정**한다 — DTO 에서 필드를 지우면 UseCase 테스트보다 여기서
 * 먼저 깨져야 한다. 컨버터는 프로덕션 등가로 맞춘다(맨 ObjectMapper 는 ProblemDetail 커스텀
 * 프로퍼티를 중첩 직렬화해 실제 응답 형태를 검증하지 못한다).
 */
class InternalGoodsOrderApiControllerTest : BehaviorSpec({

    fun buildMockMvc(findGoodsOrderHistoryUseCase: FindGoodsOrderHistoryUseCase) = MockMvcBuilders
        .standaloneSetup(InternalGoodsOrderApiController(findGoodsOrderHistoryUseCase))
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(productionEquivalentJsonConverter())
        .build()

    fun orderHistoryItem(sourceId: Long = 11L, paymentId: Long? = 701L) = InternalGoodsOrderHistoryItemResponse(
        sourceId = sourceId,
        title = "러닝화 외 1건",
        status = GoodsOrderStatus.CONFIRMED,
        paymentId = paymentId,
        createdAt = ZonedDateTime.now(),
        amount = BigDecimal("59000"),
    )

    Given("신원 헤더로 본인 굿즈 주문 이력을 조회하면") {
        val findGoodsOrderHistoryUseCase = mockk<FindGoodsOrderHistoryUseCase>()
        every {
            findGoodsOrderHistoryUseCase.execute(
                InternalGoodsOrderHistoryCriteria(userId = 7L, page = 0, size = 20),
            )
        } returns listOf(orderHistoryItem())
        val mockMvc = buildMockMvc(findGoodsOrderHistoryUseCase)

        When("GET /internal/order-history/goods 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/goods").header(INTERNAL_AUTH_SUBJECT_HEADER, "7"),
            )

            Then("200 과 함께 그 사용자의 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceId").value(11))
            }

            Then("소비자(edge OrderHistoryItem)가 요구하는 계약 필드가 응답 JSON 에 모두 실린다") {
                result.andExpect(jsonPath("$[0].title").value("러닝화 외 1건"))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$[0].paymentId").value(701))
                    .andExpect(jsonPath("$[0].amount").value(59000))
                    .andExpect(jsonPath("$[0].createdAt").exists())
            }

            Then("파사드가 만드는 필드(orderType·detailPath)는 응답에 없다") {
                result.andExpect(jsonPath("$[0].orderType").doesNotExist())
                    .andExpect(jsonPath("$[0].detailPath").doesNotExist())
            }
        }
    }

    Given("신원 헤더가 없으면") {
        val findGoodsOrderHistoryUseCase = mockk<FindGoodsOrderHistoryUseCase>()
        val mockMvc = buildMockMvc(findGoodsOrderHistoryUseCase)

        When("GET /internal/order-history/goods 요청 시") {
            val result = mockMvc.perform(get("/internal/order-history/goods"))

            Then("400 으로 거부하고 조회를 수행하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { findGoodsOrderHistoryUseCase.execute(any()) }
            }
        }
    }

    Given("신원 헤더 값이 사용자 PK 로 해석되지 않으면") {
        val findGoodsOrderHistoryUseCase = mockk<FindGoodsOrderHistoryUseCase>()
        val mockMvc = buildMockMvc(findGoodsOrderHistoryUseCase)

        When("GET /internal/order-history/goods 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/goods").header(INTERNAL_AUTH_SUBJECT_HEADER, "not-a-user-id"),
            )

            Then("400 으로 거부하고 조회를 수행하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { findGoodsOrderHistoryUseCase.execute(any()) }
            }

            Then("응답 detail 에 받은 헤더 값을 되돌려주지 않는다") {
                result.andExpect(jsonPath("$.detail").value("Invalid internal identity header"))
            }
        }
    }

    Given("결제 전(미결제) 주문만 있으면") {
        val findGoodsOrderHistoryUseCase = mockk<FindGoodsOrderHistoryUseCase>()
        every {
            findGoodsOrderHistoryUseCase.execute(
                InternalGoodsOrderHistoryCriteria(userId = 7L, page = 0, size = 20),
            )
        } returns listOf(orderHistoryItem(sourceId = 12L, paymentId = null))
        val mockMvc = buildMockMvc(findGoodsOrderHistoryUseCase)

        When("GET /internal/order-history/goods 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/goods").header(INTERNAL_AUTH_SUBJECT_HEADER, "7"),
            )

            // PR #385 가 세운 계약 — 전역 ObjectMapper 는 **null 을 키와 함께 남긴다**(NON_NULL 로
            // 키를 지우면 클라이언트가 "null" 이 아니라 "필드 없음" 을 받아 스키마가 깨진다).
            // 그래서 키 존재(hasJsonPath)와 값 null 을 함께 단언한다 — exists() 는 null 에서 실패한다.
            Then("paymentId 를 null 로 실어 보낸다 — 키가 사라지지 않는다(파사드의 미결제 판정 입력)") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].paymentId").hasJsonPath())
                    .andExpect(jsonPath("$[0].paymentId").value(nullValue()))
                    .andExpect(jsonPath("$[0].sourceId").value(12))
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val findGoodsOrderHistoryUseCase = mockk<FindGoodsOrderHistoryUseCase>()
        every { findGoodsOrderHistoryUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(findGoodsOrderHistoryUseCase)

        When("GET /internal/order-history/goods 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/goods").header(INTERNAL_AUTH_SUBJECT_HEADER, "7"),
            )

            Then("200 과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})
