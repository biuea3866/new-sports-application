package com.sportsapp.presentation.payment.controller

import com.sportsapp.application.payment.dto.PaymentResponse
import com.sportsapp.application.payment.dto.PreparePaymentResponse
import com.sportsapp.application.payment.usecase.CreatePaymentUseCase
import com.sportsapp.application.payment.usecase.GetPaymentUseCase
import com.sportsapp.application.payment.usecase.ListMyPaymentsUseCase
import com.sportsapp.application.payment.usecase.PreparePaymentUseCase
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/**
 * AUTH-04 — `POST /payments`는 이전에 `userId = 1L`이 하드코딩돼 principal과 무관하게 항상
 * 사용자 1로 결제가 귀속되는 버그가 있었다. principal.id로 전달되는지 직접 검증한다.
 */
class PaymentApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        createPaymentUseCase: CreatePaymentUseCase = mockk(),
        preparePaymentUseCase: PreparePaymentUseCase = mockk(),
        getPaymentUseCase: GetPaymentUseCase = mockk(),
        listMyPaymentsUseCase: ListMyPaymentsUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        PaymentApiController(createPaymentUseCase, preparePaymentUseCase, getPaymentUseCase, listMyPaymentsUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    Given("로그인한 사용자의 결제 생성 요청") {
        val createPaymentUseCase = mockk<CreatePaymentUseCase>()
        val now = ZonedDateTime.now()
        every {
            createPaymentUseCase.execute(match { it.userId == TEST_USER_ID })
        } returns PaymentResponse(
            id = 1L,
            orderType = OrderType.BOOKING,
            orderId = 1L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            status = PaymentStatus.PENDING,
            createdAt = now,
            paidAt = null,
            checkoutUrl = null,
        )
        val mockMvc = buildMockMvc(createPaymentUseCase = createPaymentUseCase)

        When("POST /payments 요청 시") {
            val body = """{"orderType":"BOOKING","orderId":1,"method":"CREDIT_CARD","amount":10000,"currency":"KRW"}"""
            val result = mockMvc.perform(
                post("/payments").header("Idempotency-Key", "idem-1").contentType(MediaType.APPLICATION_JSON).content(body),
            )

            Then("principal.id 로 결제가 생성되고(하드코딩된 1L 버그 수정) 201을 반환한다") {
                result.andExpect(status().isCreated)
                verify(exactly = 1) { createPaymentUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 결제 준비 요청") {
        val preparePaymentUseCase = mockk<PreparePaymentUseCase>()
        every {
            preparePaymentUseCase.execute(match { it.userId == TEST_USER_ID })
        } returns PreparePaymentResponse(paymentId = 1L, checkoutUrl = "http://checkout", pgTransactionId = "txn-1")
        val mockMvc = buildMockMvc(preparePaymentUseCase = preparePaymentUseCase)

        When("POST /payments/prepare 요청 시") {
            val body = """
                {"orderType":"BOOKING","orderId":1,"method":"CREDIT_CARD","amount":10000,"currency":"KRW",
                 "itemName":"테스트","returnUrl":"http://return","failUrl":"http://fail"}
            """.trimIndent()
            val result = mockMvc.perform(
                post("/payments/prepare").header("Idempotency-Key", "idem-2").contentType(MediaType.APPLICATION_JSON).content(body),
            )

            Then("principal.id 로 준비되고 201을 반환한다") {
                result.andExpect(status().isCreated)
                    .andExpect(jsonPath("$.checkoutUrl").value("http://checkout"))
                verify(exactly = 1) { preparePaymentUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 결제 단건 조회 요청") {
        val getPaymentUseCase = mockk<GetPaymentUseCase>()
        val now = ZonedDateTime.now()
        every {
            getPaymentUseCase.execute(userId = TEST_USER_ID, paymentId = 5L)
        } returns PaymentResponse(
            id = 5L,
            orderType = OrderType.BOOKING,
            orderId = 1L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            status = PaymentStatus.COMPLETED,
            createdAt = now,
            paidAt = now,
            checkoutUrl = null,
        )
        val mockMvc = buildMockMvc(getPaymentUseCase = getPaymentUseCase)

        When("GET /payments/5 요청 시") {
            val result = mockMvc.perform(get("/payments/5"))

            Then("200과 함께 결제 정보를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(5))
            }
        }
    }

    Given("본인 결제 목록 조회 요청") {
        val listMyPaymentsUseCase = mockk<ListMyPaymentsUseCase>()
        every {
            listMyPaymentsUseCase.execute(match { it.userId == TEST_USER_ID })
        } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)
        val mockMvc = buildMockMvc(listMyPaymentsUseCase = listMyPaymentsUseCase)

        When("GET /payments/me 요청 시") {
            val result = mockMvc.perform(get("/payments/me"))

            Then("principal.id 기준으로 조회되고 200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { listMyPaymentsUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }
})
