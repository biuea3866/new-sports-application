package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.dto.GenerateSlotsResult
import com.sportsapp.application.booking.usecase.GenerateSlotsUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GenerateSlotsSchedulerTest : BehaviorSpec({

    Given("자동 슬롯 생성 cron 트리거 시각") {
        val generateSlotsUseCase = mockk<GenerateSlotsUseCase>()
        val scheduler = GenerateSlotsScheduler(generateSlotsUseCase, windowDays = 14)

        every { generateSlotsUseCase.execute(14) } returns GenerateSlotsResult(emptyList())

        When("generateSlots를 호출하면") {
            scheduler.generateSlots()

            Then("설정된 windowDays로 GenerateSlotsUseCase를 1회 호출한다") {
                verify(exactly = 1) { generateSlotsUseCase.execute(14) }
            }
        }
    }
})
