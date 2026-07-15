package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.usecase.CloseSlotUseCase
import com.sportsapp.application.booking.usecase.CreateSlotUseCase
import com.sportsapp.application.booking.usecase.DeleteSlotUseCase
import com.sportsapp.application.booking.usecase.ListSlotsUseCase
import com.sportsapp.application.booking.usecase.OpenSlotUseCase
import com.sportsapp.application.booking.usecase.UpdateSlotUseCase
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.exception.UnauthorizedSlotAccessException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val OWNER_USER_ID = 1L
private const val OTHER_USER_ID = 999L

/**
 * AUTH-04 — 슬롯 생성·변경·개폐·삭제는 `@AuthenticationPrincipal UserPrincipal`(시설주 신원)로
 * 식별한다. `listSlots`(GET)는 공개 브라우징이라 인증이 필요 없다(SecurityConfig GET permitAll).
 */
class SlotApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        createSlotUseCase: CreateSlotUseCase = mockk(),
        updateSlotUseCase: UpdateSlotUseCase = mockk(),
        listSlotsUseCase: ListSlotsUseCase = mockk(),
        deleteSlotUseCase: DeleteSlotUseCase = mockk(),
        closeSlotUseCase: CloseSlotUseCase = mockk(),
        openSlotUseCase: OpenSlotUseCase = mockk(),
        userId: Long = OWNER_USER_ID,
    ) = MockMvcBuilders.standaloneSetup(
        SlotApiController(createSlotUseCase, updateSlotUseCase, listSlotsUseCase, deleteSlotUseCase, closeSlotUseCase, openSlotUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
        .build()

    fun slot(id: Long = 1L, ownerId: Long = OWNER_USER_ID) = Slot.create(
        facilityId = "FAC-01",
        date = ZonedDateTime.now().plusDays(1),
        timeRange = "10:00-11:00",
        capacity = 5,
        ownerId = ownerId,
    )

    Given("시설주가 슬롯을 생성하는 요청") {
        val createSlotUseCase = mockk<CreateSlotUseCase>()
        every { createSlotUseCase.execute(match { it.ownerId == OWNER_USER_ID && it.facilityId == "FAC-01" }) } returns slot()
        val mockMvc = buildMockMvc(createSlotUseCase = createSlotUseCase)

        When("POST /facilities/FAC-01/slots 요청 시") {
            val body = """{"date":"2026-08-01T10:00:00+09:00","timeRange":"10:00-11:00","capacity":5}"""
            val result = mockMvc.perform(post("/facilities/FAC-01/slots").contentType(MediaType.APPLICATION_JSON).content(body))

            Then("principal.id 로 슬롯이 생성되고 201을 반환한다") {
                result.andExpect(status().isCreated)
                verify(exactly = 1) { createSlotUseCase.execute(match { it.ownerId == OWNER_USER_ID }) }
            }
        }
    }

    Given("인증 없이 슬롯 목록을 조회하면 (공개 브라우징)") {
        val listSlotsUseCase = mockk<ListSlotsUseCase>()
        every { listSlotsUseCase.execute("FAC-01", null) } returns listOf(slot())
        val mockMvc = buildMockMvc(listSlotsUseCase = listSlotsUseCase)

        When("GET /facilities/FAC-01/slots 요청 시") {
            val result = mockMvc.perform(get("/facilities/FAC-01/slots"))

            Then("200과 함께 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }
    }

    Given("소유자가 아닌 사용자의 슬롯 변경 요청") {
        val updateSlotUseCase = mockk<UpdateSlotUseCase>()
        every { updateSlotUseCase.execute(any()) } throws UnauthorizedSlotAccessException(1L)
        val mockMvc = buildMockMvc(updateSlotUseCase = updateSlotUseCase, userId = OTHER_USER_ID)

        When("PATCH /facilities/FAC-01/slots/1 요청 시") {
            val result = mockMvc.perform(
                patch("/facilities/FAC-01/slots/1").contentType(MediaType.APPLICATION_JSON).content("""{"capacity":10}"""),
            )

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
            }
        }
    }

    Given("소유자의 슬롯 삭제 요청") {
        val deleteSlotUseCase = mockk<DeleteSlotUseCase>()
        every { deleteSlotUseCase.execute(match { it.requesterId == OWNER_USER_ID && it.slotId == 1L }) } returns Unit
        val mockMvc = buildMockMvc(deleteSlotUseCase = deleteSlotUseCase)

        When("DELETE /facilities/FAC-01/slots/1 요청 시") {
            val result = mockMvc.perform(delete("/facilities/FAC-01/slots/1"))

            Then("204를 반환한다") {
                result.andExpect(status().isNoContent)
                verify(exactly = 1) { deleteSlotUseCase.execute(match { it.requesterId == OWNER_USER_ID }) }
            }
        }
    }
})
