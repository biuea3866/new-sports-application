package com.sportsapp.application.booking

import com.sportsapp.application.booking.usecase.ListSlotsUseCase
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListSlotsUseCaseTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val useCase = ListSlotsUseCase(slotDomainService)

    Given("programId 필터를 지정한 조회") {
        val slots = listOf(
            Slot.create(
                facilityId = "FAC-01",
                date = ZonedDateTime.now(),
                timeRange = "09:00-10:00",
                capacity = 5,
                ownerId = 1L,
                programId = 10L,
            ),
        )
        every { slotDomainService.listSlots("FAC-01", 10L) } returns slots

        When("execute(facilityId, programId)를 호출하면") {
            val result = useCase.execute("FAC-01", 10L)

            Then("필터링된 슬롯 목록이 반환된다") {
                result shouldBe slots
            }
        }
    }

    Given("programId 없이 조회") {
        val slots = listOf(
            Slot.create(
                facilityId = "FAC-01",
                date = ZonedDateTime.now(),
                timeRange = "09:00-10:00",
                capacity = 5,
                ownerId = 1L,
            ),
        )
        every { slotDomainService.listSlots("FAC-01", null) } returns slots

        When("execute(facilityId, null)를 호출하면") {
            val result = useCase.execute("FAC-01", null)

            Then("시설 전체 슬롯이 반환된다") {
                result shouldBe slots
            }
        }
    }
})
