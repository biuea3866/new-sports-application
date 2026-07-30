package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.dto.SelectSeatsResponse
import com.sportsapp.application.ticketing.usecase.GetEventUseCase
import com.sportsapp.application.ticketing.usecase.ListEventsUseCase
import com.sportsapp.application.ticketing.usecase.ReleaseSeatsUseCase
import com.sportsapp.application.ticketing.usecase.SelectSeatsUseCase
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/** AUTH-04 — 좌석 선점·해제는 `@AuthenticationPrincipal UserPrincipal`(non-null)로 식별한다. */
class EventApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        listEventsUseCase: ListEventsUseCase = mockk(),
        getEventUseCase: GetEventUseCase = mockk(),
        selectSeatsUseCase: SelectSeatsUseCase = mockk(),
        releaseSeatsUseCase: ReleaseSeatsUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        EventApiController(listEventsUseCase, getEventUseCase, selectSeatsUseCase, releaseSeatsUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    Given("로그인한 사용자의 좌석 선점 요청") {
        val selectSeatsUseCase = mockk<SelectSeatsUseCase>()
        every {
            selectSeatsUseCase.execute(match { it.eventId == 1L && it.userId == TEST_USER_ID && it.seatIds == listOf(10L) })
        } returns SelectSeatsResponse(lockId = "lock-1", expiresAt = ZonedDateTime.now().plusMinutes(5))
        val mockMvc = buildMockMvc(selectSeatsUseCase = selectSeatsUseCase)

        When("POST /events/1/seats/select 요청 시") {
            val result = mockMvc.perform(
                post("/events/1/seats/select").contentType(MediaType.APPLICATION_JSON).content("""{"seatIds":[10]}"""),
            )

            Then("principal.id 로 선점되고 200과 lockId를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.lockId").value("lock-1"))
                verify(exactly = 1) { selectSeatsUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("로그인한 사용자의 좌석 해제 요청") {
        val releaseSeatsUseCase = mockk<ReleaseSeatsUseCase>()
        every { releaseSeatsUseCase.execute(match { it.eventId == 1L && it.userId == TEST_USER_ID }) } returns Unit
        val mockMvc = buildMockMvc(releaseSeatsUseCase = releaseSeatsUseCase)

        When("POST /events/1/seats/release 요청 시") {
            val result = mockMvc.perform(
                post("/events/1/seats/release").contentType(MediaType.APPLICATION_JSON).content("""{"seatIds":[10]}"""),
            )

            Then("204를 반환한다") {
                result.andExpect(status().isNoContent)
                verify(exactly = 1) { releaseSeatsUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }
})
