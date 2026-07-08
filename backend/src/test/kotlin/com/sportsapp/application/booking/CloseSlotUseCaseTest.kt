package com.sportsapp.application.booking

import com.sportsapp.application.booking.dto.CloseSlotCommand
import com.sportsapp.application.booking.usecase.CloseSlotUseCase
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

class CloseSlotUseCaseTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val useCase = CloseSlotUseCase(slotDomainService)

    Given("소유자가 슬롯을 close하는 CloseSlotCommand") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        slot.close(1L)
        val command = CloseSlotCommand(requesterId = 1L, slotId = 1L)
        every { slotDomainService.closeSlot(1L, 1L) } returns slot

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("status가 CLOSED인 Slot이 반환된다") {
                result.status shouldBe SlotStatus.CLOSED
            }
        }
    }

    Given("소유자가 아닌 사용자의 CloseSlotCommand") {
        val command = CloseSlotCommand(requesterId = 99L, slotId = 1L)
        every { slotDomainService.closeSlot(99L, 1L) } throws UnauthorizedSlotAccessException(1L)

        When("execute를 호출하면") {
            Then("UnauthorizedSlotAccessException을 던진다") {
                shouldThrow<UnauthorizedSlotAccessException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
