package com.sportsapp.application.booking

import com.sportsapp.application.booking.dto.OpenSlotCommand
import com.sportsapp.application.booking.usecase.OpenSlotUseCase
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.entity.SlotStatus
import com.sportsapp.domain.booking.exception.UnauthorizedSlotAccessException
import com.sportsapp.domain.booking.service.SlotDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class OpenSlotUseCaseTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val useCase = OpenSlotUseCase(slotDomainService)

    Given("소유자가 CLOSED 슬롯을 open하는 OpenSlotCommand") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        val command = OpenSlotCommand(requesterId = 1L, slotId = 1L)
        every { slotDomainService.openSlot(1L, 1L) } returns slot

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("status가 OPEN인 Slot이 반환된다") {
                result.status shouldBe SlotStatus.OPEN
            }
        }
    }

    Given("소유자가 아닌 사용자의 OpenSlotCommand") {
        val command = OpenSlotCommand(requesterId = 99L, slotId = 1L)
        every { slotDomainService.openSlot(99L, 1L) } throws UnauthorizedSlotAccessException(1L)

        When("execute를 호출하면") {
            Then("UnauthorizedSlotAccessException을 던진다") {
                shouldThrow<UnauthorizedSlotAccessException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
