package com.sportsapp.application.booking

import com.sportsapp.domain.booking.exception.InvalidSlotException
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import com.sportsapp.application.booking.usecase.CreateSlotUseCase
import com.sportsapp.application.booking.dto.CreateSlotCommand

class CreateSlotUseCaseTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val useCase = CreateSlotUseCase(slotDomainService)

    Given("정상 파라미터의 CreateSlotCommand") {
        val command = CreateSlotCommand(
            ownerId = 1L,
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
        )
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = command.date,
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        every {
            slotDomainService.createSlot(
                ownerId = 1L,
                facilityId = "FAC-01",
                date = command.date,
                timeRange = "09:00-10:00",
                capacity = 5,
            )
        } returns slot

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] SlotResponse가 반환된다") {
                result.facilityId shouldBe "FAC-01"
                result.timeRange shouldBe "09:00-10:00"
                result.capacity shouldBe 5
                result.ownerId shouldBe 1L
            }
        }
    }

    Given("capacity=0인 CreateSlotCommand") {
        val command = CreateSlotCommand(
            ownerId = 1L,
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 0,
        )
        every {
            slotDomainService.createSlot(
                ownerId = any(),
                facilityId = any(),
                date = any(),
                timeRange = any(),
                capacity = 0,
            )
        } throws InvalidSlotException("capacity must be positive, got: 0")

        When("execute를 호출하면") {
            Then("[U-01] InvalidSlotException을 던진다") {
                shouldThrow<InvalidSlotException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
