package com.sportsapp.application.booking

import com.sportsapp.application.booking.usecase.GenerateSlotsUseCase
import com.sportsapp.domain.booking.dto.FacilitySlotGenerationOutcome
import com.sportsapp.domain.booking.service.SlotGenerationDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GenerateSlotsUseCaseTest : BehaviorSpec({

    val slotGenerationDomainService = mockk<SlotGenerationDomainService>()
    val useCase = GenerateSlotsUseCase(slotGenerationDomainService)

    Given("일부 시설은 성공하고 일부는 실패한 슬롯 생성 결과") {
        val outcomes = listOf(
            FacilitySlotGenerationOutcome(facilityId = "FAC-01", createdCount = 14, succeeded = true),
            FacilitySlotGenerationOutcome(facilityId = "FAC-02", createdCount = 0, succeeded = false),
        )
        every { slotGenerationDomainService.generateAll(14) } returns outcomes

        When("execute(windowDays=14)를 호출하면") {
            val result = useCase.execute(windowDays = 14)

            Then("SlotGenerationDomainService.generateAll을 windowDays로 호출한다") {
                verify(exactly = 1) { slotGenerationDomainService.generateAll(14) }
            }

            Then("시설별 결과를 집계한 GenerateSlotsResult를 반환한다") {
                result.outcomes shouldBe outcomes
                result.totalCreated shouldBe 14
                result.failedFacilityCount shouldBe 1
            }
        }
    }

    Given("모든 시설이 성공한 슬롯 생성 결과") {
        val outcomes = listOf(
            FacilitySlotGenerationOutcome(facilityId = "FAC-01", createdCount = 5, succeeded = true),
            FacilitySlotGenerationOutcome(facilityId = "FAC-02", createdCount = 7, succeeded = true),
        )
        every { slotGenerationDomainService.generateAll(14) } returns outcomes

        When("execute(windowDays=14)를 호출하면") {
            val result = useCase.execute(windowDays = 14)

            Then("실패한 시설이 없다") {
                result.failedFacilityCount shouldBe 0
                result.totalCreated shouldBe 12
            }
        }
    }
})
