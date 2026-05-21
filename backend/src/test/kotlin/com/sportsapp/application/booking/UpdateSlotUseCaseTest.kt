package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotDomainService
import com.sportsapp.domain.booking.UnauthorizedSlotAccessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class UpdateSlotUseCaseTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val useCase = UpdateSlotUseCase(slotDomainService)

    Given("소유자가 capacity를 변경하는 UpdateSlotCommand") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 10,
            ownerId = 1L,
        )
        val command = UpdateSlotCommand(
            requesterId = 1L,
            slotId = 99L,
            timeRange = null,
            capacity = 10,
        )
        every {
            slotDomainService.updateSlot(
                requesterId = 1L,
                slotId = 99L,
                newTimeRange = null,
                newCapacity = 10,
            )
        } returns slot

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] 변경된 SlotResponse가 반환된다") {
                result.capacity shouldBe 10
                result.ownerId shouldBe 1L
            }
        }
    }

    Given("타인이 슬롯 수정을 시도하는 UpdateSlotCommand") {
        val command = UpdateSlotCommand(
            requesterId = 99L,
            slotId = 1L,
            timeRange = null,
            capacity = 5,
        )
        every {
            slotDomainService.updateSlot(
                requesterId = 99L,
                slotId = 1L,
                newTimeRange = null,
                newCapacity = 5,
            )
        } throws UnauthorizedSlotAccessException(1L)

        When("execute를 호출하면") {
            Then("[U-02] UnauthorizedSlotAccessException을 던진다") {
                shouldThrow<UnauthorizedSlotAccessException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
