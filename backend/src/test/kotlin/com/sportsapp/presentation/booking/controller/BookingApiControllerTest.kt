package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.CreateBookingResult
import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.application.booking.dto.ListBookingsResult
import com.sportsapp.application.booking.usecase.CancelBookingUseCase
import com.sportsapp.application.booking.usecase.CreateBookingUseCase
import com.sportsapp.application.booking.usecase.GetBookingUseCase
import com.sportsapp.application.booking.usecase.ListMyBookingsUseCase
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/**
 * AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`(`@AuthenticationPrincipal
 * UserPrincipal`)로 신원을 식별한다. standalone MockMvc이므로 실제 Spring Security 필터체인
 * 없이 [fixedPrincipalResolver]로 고정 사용자를 해석해 컨트롤러 로직만 검증한다
 * (CommunityApiControllerTest 선례). "인증 없으면 401"은 실 필터체인이 필요해
 * `scenario/security/AuthDomainMigrationSecurityScenarioTest`가 담당한다.
 */
class BookingApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        listMyBookingsUseCase: ListMyBookingsUseCase = mockk(),
        getBookingUseCase: GetBookingUseCase = mockk(),
        createBookingUseCase: CreateBookingUseCase = mockk(),
        cancelBookingUseCase: CancelBookingUseCase = mockk(),
        userId: Long = TEST_USER_ID,
    ) = MockMvcBuilders.standaloneSetup(
        BookingApiController(listMyBookingsUseCase, getBookingUseCase, createBookingUseCase, cancelBookingUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
        .build()

    Given("로그인한 사용자의 예약 생성 요청") {
        val createBookingUseCase = mockk<CreateBookingUseCase>()
        every {
            createBookingUseCase.execute(match { it.userId == TEST_USER_ID && it.slotId == 1L })
        } returns CreateBookingResult(bookingId = 1L, slotId = 1L, userId = TEST_USER_ID, status = BookingStatus.PENDING, paymentId = 9L)
        val mockMvc = buildMockMvc(createBookingUseCase = createBookingUseCase)

        When("POST /bookings 요청 시") {
            val body = """{"slotId":1,"paymentMethod":"CREDIT_CARD","amount":10000,"currency":"KRW"}"""
            val result = mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(body))

            Then("principal.id 로 CreateBookingCommand 가 생성되고 202를 반환한다") {
                result.andExpect(status().isAccepted)
                    .andExpect(jsonPath("$.bookingId").value(1))
                verify(exactly = 1) { createBookingUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 예약 목록 조회 요청") {
        val listMyBookingsUseCase = mockk<ListMyBookingsUseCase>()
        every {
            listMyBookingsUseCase.execute(match { it.userId == TEST_USER_ID })
        } returns ListBookingsResult(bookings = emptyList(), totalElements = 0, totalPages = 0, page = 0, size = 20)
        val mockMvc = buildMockMvc(listMyBookingsUseCase = listMyBookingsUseCase)

        When("GET /bookings/me 요청 시") {
            val result = mockMvc.perform(get("/bookings/me"))

            Then("principal.id 기준으로 조회되고 200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { listMyBookingsUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 예약 상세 조회 요청") {
        val getBookingUseCase = mockk<GetBookingUseCase>()
        val now = ZonedDateTime.now()
        every {
            getBookingUseCase.execute(requesterId = TEST_USER_ID, bookingId = 5L)
        } returns GetBookingResult(
            id = 5L,
            slotId = 1L,
            facilityId = "FAC-01",
            userId = TEST_USER_ID,
            status = BookingStatus.CONFIRMED,
            paymentId = 9L,
            paymentStatus = null,
            title = "테니스장",
            createdAt = now,
            updatedAt = now,
        )
        val mockMvc = buildMockMvc(getBookingUseCase = getBookingUseCase)

        When("GET /bookings/5 요청 시") {
            val result = mockMvc.perform(get("/bookings/5"))

            Then("200과 함께 상세를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(5))
            }
        }
    }

    Given("타인 예약을 취소하려는 요청") {
        val cancelBookingUseCase = mockk<CancelBookingUseCase>()
        every { cancelBookingUseCase.execute(any()) } throws UnauthorizedBookingAccessException(7L)
        val mockMvc = buildMockMvc(cancelBookingUseCase = cancelBookingUseCase, userId = 999L)

        When("POST /bookings/7/cancel 요청 시") {
            val result = mockMvc.perform(post("/bookings/7/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
            }
        }
    }
})
