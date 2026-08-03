package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.application.booking.dto.ListBookingsResult
import com.sportsapp.application.booking.dto.ListFacilityOwnerBookingsCommand
import com.sportsapp.application.booking.usecase.ListFacilityOwnerBookingsUseCase
import com.sportsapp.domain.booking.entity.BookingStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import sportsapp.testkit.presentation.support.fixedPrincipalResolver
import java.time.ZonedDateTime

private const val PARTNER_USER_ID = 69L

/**
 * 파트너 예약 관리 API — 조회 범위를 **서버가 인증 주체로 결정**하는지 검증한다.
 *
 * 클라이언트가 ownerUserId를 지정할 수 있으면 남의 시설 예약을 조회할 수 있으므로,
 * 인증된 principal.id가 그대로 소유자 스코프로 넘어가는지가 이 컨트롤러의 핵심 책임이다.
 */
class FacilityOwnerBookingApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        listFacilityOwnerBookingsUseCase: ListFacilityOwnerBookingsUseCase = mockk(),
        userId: Long = PARTNER_USER_ID,
    ) = MockMvcBuilders.standaloneSetup(
        FacilityOwnerBookingApiController(listFacilityOwnerBookingsUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
        .build()

    fun buildBookingResult(id: Long, bookerUserId: Long) = GetBookingResult(
        id = id,
        slotId = 2L,
        facilityId = null,
        userId = bookerUserId,
        status = BookingStatus.CONFIRMED,
        paymentId = 500L,
        paymentStatus = null,
        title = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    Given("내 시설에 다른 사람이 건 예약이 있을 때") {
        val useCase = mockk<ListFacilityOwnerBookingsUseCase>()
        val commandSlot = slot<ListFacilityOwnerBookingsCommand>()
        every { useCase.execute(capture(commandSlot)) } returns ListBookingsResult(
            bookings = listOf(buildBookingResult(id = 1L, bookerUserId = 68L)),
            totalElements = 1L,
            totalPages = 1,
            page = 0,
            size = 20,
        )
        val mockMvc = buildMockMvc(useCase)

        When("예약 목록을 조회하면") {
            val response = mockMvc.perform(get("/api/facility-owner/bookings"))

            Then("200과 예약 목록이 반환된다") {
                response.andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.bookings[0].id").value(1))
            }

            // 클라이언트가 조회 대상을 고를 수 없어야 한다 — 서버가 인증 주체로 고정한다.
            Then("인증된 파트너 id가 소유자 스코프로 전달된다") {
                commandSlot.captured.ownerUserId shouldBe PARTNER_USER_ID
            }
        }
    }

    Given("상태 필터와 페이지를 지정했을 때") {
        val useCase = mockk<ListFacilityOwnerBookingsUseCase>()
        val commandSlot = slot<ListFacilityOwnerBookingsCommand>()
        every { useCase.execute(capture(commandSlot)) } returns ListBookingsResult(
            bookings = emptyList(),
            totalElements = 0L,
            totalPages = 0,
            page = 1,
            size = 5,
        )
        val mockMvc = buildMockMvc(useCase)

        When("쿼리 파라미터로 조회하면") {
            mockMvc.perform(get("/api/facility-owner/bookings?status=CONFIRMED&page=1&size=5"))

            Then("상태·페이지가 그대로 커맨드에 담긴다") {
                commandSlot.captured.status shouldBe BookingStatus.CONFIRMED
                commandSlot.captured.pageable.pageNumber shouldBe 1
                commandSlot.captured.pageable.pageSize shouldBe 5
            }
        }
    }

    Given("내 시설에 예약이 없을 때") {
        val useCase = mockk<ListFacilityOwnerBookingsUseCase>()
        every { useCase.execute(any()) } returns ListBookingsResult(
            bookings = emptyList(),
            totalElements = 0L,
            totalPages = 0,
            page = 0,
            size = 20,
        )
        val mockMvc = buildMockMvc(useCase)

        When("예약 목록을 조회하면") {
            val response = mockMvc.perform(get("/api/facility-owner/bookings"))

            Then("200과 빈 목록이 반환된다") {
                response.andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(0))
                        .andExpect(jsonPath("$.bookings").isEmpty)
            }

            Then("UseCase가 한 번 호출된다") {
                verify(exactly = 1) { useCase.execute(any()) }
            }
        }
    }
})
